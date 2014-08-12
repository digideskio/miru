package com.jivesoftware.os.miru.service.index;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.jivesoftware.os.jive.utils.chunk.store.ChunkStore;
import com.jivesoftware.os.jive.utils.chunk.store.ChunkStoreInitializer;
import com.jivesoftware.os.jive.utils.id.Id;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.service.index.disk.MiruOnDiskUnreadTrackingIndex;
import com.jivesoftware.os.miru.service.index.memory.MiruInMemoryInvertedIndex;
import com.jivesoftware.os.miru.service.index.memory.MiruInMemoryUnreadTrackingIndex;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class MiruUnreadTrackingIndexTest {

    @Test(dataProvider = "miruUnreadTrackingIndexDataProviderWithData")
    public void testDefaultData(MiruUnreadTrackingIndex miruUnreadTrackingIndex, MiruStreamId streamId, MiruInvertedIndex invertedIndex) throws Exception {
        Optional<EWAHCompressedBitmap> unread = miruUnreadTrackingIndex.getUnread(streamId);
        assertNotNull(unread);
        assertTrue(unread.isPresent());
        assertEquals(unread.get(), invertedIndex.getIndex());
    }

    @Test(dataProvider = "miruUnreadTrackingIndexDataProviderWithoutData")
    public void testIndex(MiruUnreadTrackingIndex miruUnreadTrackingIndex, MiruStreamId streamId, MiruInvertedIndex invertedIndex) throws Exception {
        Optional<EWAHCompressedBitmap> unreadIndex = miruUnreadTrackingIndex.getUnread(streamId);
        assertNotNull(unreadIndex);
        assertFalse(unreadIndex.isPresent());

        miruUnreadTrackingIndex.index(streamId, 1);
        miruUnreadTrackingIndex.index(streamId, 3);
        miruUnreadTrackingIndex.index(streamId, 5);

        unreadIndex = miruUnreadTrackingIndex.getUnread(streamId);
        assertNotNull(unreadIndex);
        assertTrue(unreadIndex.isPresent());
        EWAHCompressedBitmap unreadBitmap = unreadIndex.get();
        assertTrue(unreadBitmap.get(1));
        assertFalse(unreadBitmap.get(2));
        assertTrue(unreadBitmap.get(3));
        assertFalse(unreadBitmap.get(4));
        assertTrue(unreadBitmap.get(5));
    }

    @Test(dataProvider = "miruUnreadTrackingIndexDataProvider")
    public void testUnread(MiruUnreadTrackingIndex miruUnreadTrackingIndex, MiruStreamId streamId, MiruInvertedIndex invertedIndex) throws Exception {
        EWAHCompressedBitmap unread = miruUnreadTrackingIndex.getUnread(streamId).get();
        assertEquals(unread.cardinality(), 0);

        miruUnreadTrackingIndex.index(streamId, 1);
        miruUnreadTrackingIndex.index(streamId, 3);

        EWAHCompressedBitmap readMask = new EWAHCompressedBitmap();
        readMask.set(1);
        readMask.set(2);
        miruUnreadTrackingIndex.applyRead(streamId, readMask);

        unread = miruUnreadTrackingIndex.getUnread(streamId).get();
        assertEquals(unread.cardinality(), 1);
        assertTrue(unread.get(3));

        EWAHCompressedBitmap unreadMask = new EWAHCompressedBitmap();
        unreadMask.set(1);
        unreadMask.set(3);
        miruUnreadTrackingIndex.applyUnread(streamId, unreadMask);

        unread = miruUnreadTrackingIndex.getUnread(streamId).get();
        assertEquals(unread.cardinality(), 2);
        assertTrue(unread.get(1));
        assertTrue(unread.get(3));
    }

    @Test(dataProvider = "miruUnreadTrackingIndexDataProviderWithData")
    public void testRead(MiruUnreadTrackingIndex miruUnreadTrackingIndex, MiruStreamId streamId, MiruInvertedIndex invertedIndex) throws Exception {
        EWAHCompressedBitmap unread = miruUnreadTrackingIndex.getUnread(streamId).get();
        assertEquals(unread.cardinality(), 3);

        EWAHCompressedBitmap readMask = new EWAHCompressedBitmap();
        readMask.set(1);
        readMask.set(2);
        readMask.set(5);
        miruUnreadTrackingIndex.applyRead(streamId, readMask);

        unread = miruUnreadTrackingIndex.getUnread(streamId).get();
        assertEquals(unread.cardinality(), 1);
        assertTrue(unread.get(3));
    }

    @DataProvider(name = "miruUnreadTrackingIndexDataProvider")
    public Object[][] miruUnreadTrackingIndexDataProvider() throws Exception {
        EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
        return generateUnreadIndexes(bitmap, true);
    }

    @DataProvider(name = "miruUnreadTrackingIndexDataProviderWithoutData")
    public Object[][] miruUnreadTrackingIndexDataProviderWithoutData() throws Exception {
        EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
        return generateUnreadIndexes(bitmap, false);
    }

    @DataProvider(name = "miruUnreadTrackingIndexDataProviderWithData")
    public Object[][] miruUnreadTrackingIndexDataProviderWithData() throws Exception {
        EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
        bitmap.set(1);
        bitmap.set(2);
        bitmap.set(3);
        return generateUnreadIndexes(bitmap, true);
    }

    private Object[][] generateUnreadIndexes(EWAHCompressedBitmap bitmap, boolean autoCreate) throws Exception {
        final MiruStreamId streamId = new MiruStreamId(new Id(12345).toBytes());
        MiruInMemoryUnreadTrackingIndex miruInMemoryUnreadTrackingIndex = new MiruInMemoryUnreadTrackingIndex();

        MiruInvertedIndex miruInvertedIndex = null;
        if (autoCreate) {
            final MiruInvertedIndex tempMiruInvertedIndex = new MiruInMemoryInvertedIndex(bitmap);
            miruInMemoryUnreadTrackingIndex.bulkImport(new BulkExport<Map<MiruStreamId, MiruInvertedIndex>>() {
                @Override
                public Map<MiruStreamId, MiruInvertedIndex> bulkExport() throws Exception {
                    return ImmutableMap.of(
                        streamId, tempMiruInvertedIndex
                    );
                }
            });
            miruInvertedIndex = tempMiruInvertedIndex;
        }

        File mapDir = Files.createTempDirectory("map").toFile();
        File swapDir = Files.createTempDirectory("swap").toFile();
        Path chunksDir = Files.createTempDirectory("chunks");
        File chunks = new File(chunksDir.toFile(), "chunks.data");
        ChunkStore chunkStore = new ChunkStoreInitializer().initialize(chunks.getAbsolutePath(), 4096, false);
        MiruOnDiskUnreadTrackingIndex miruOnDiskUnreadTrackingIndex = new MiruOnDiskUnreadTrackingIndex(mapDir, swapDir, chunkStore);
        miruOnDiskUnreadTrackingIndex.bulkImport(miruInMemoryUnreadTrackingIndex);

        return new Object[][] {
            { miruInMemoryUnreadTrackingIndex, streamId, miruInvertedIndex },
            { miruOnDiskUnreadTrackingIndex, streamId, miruInvertedIndex }
        };
    }
}
