package org.roaringbitmap.buffer;

import java.util.Arrays;

/**
 *
 */
public class RoaringBufferInspection {

    public static long sizeInBits(ImmutableRoaringBitmap bitmap) {
        int pos = bitmap.highLowContainer.size() - 1;
        if (pos >= 0) {
            return (BufferUtil.toIntUnsigned(bitmap.highLowContainer.getKeyAtIndex(pos)) + 1) << 16;
        } else {
            return 0;
        }
    }

    public static void cardinalityInBuckets(ImmutableRoaringBitmap bitmap, int[][] indexes, long[][] buckets) {
        // indexes = { 10, 20, 30, 40, 50 } length=5
        // buckets = { 10-19, 20-29, 30-39, 40-49 } length=4
        int numContainers = bitmap.highLowContainer.size();
        //System.out.println("NumContainers=" + numContainers);
        int bucketLength = buckets.length;
        int[] currentBucket = new int[bucketLength];
        Arrays.fill(currentBucket, 0);
        int[] currentBucketStart = new int[bucketLength];
        int[] currentBucketEnd = new int[bucketLength];
        for (int bi = 0; bi < bucketLength; bi++) {
            currentBucketStart[bi] = indexes[bi][currentBucket[bi]];
            currentBucketEnd[bi] = indexes[bi][currentBucket[bi] + 1];
        }

        int numExhausted = 0;
        boolean[] exhausted = new boolean[bucketLength];

        for (int pos = 0; pos < numContainers; pos++) {
            //System.out.println("pos=" + pos);
            int min = containerMin(bitmap, pos);
            for (int bi = 0; bi < bucketLength; bi++) {
                while (!exhausted[bi] && min >= currentBucketEnd[bi]) {
                    //System.out.println("Advance1 min:" + min + " >= currentBucketEnd:" + currentBucketEnd);
                    currentBucket[bi]++;
                    if (currentBucket[bi] == buckets[bi].length) {
                        numExhausted++;
                        exhausted[bi] = true;
                        break;
                    }
                    currentBucketStart[bi] = indexes[bi][currentBucket[bi]];
                    currentBucketEnd[bi] = indexes[bi][currentBucket[bi] + 1];
                }
            }
            if (numExhausted == bucketLength) {
                break;
            }

            boolean[] candidate = new boolean[bucketLength];
            boolean anyCandidates = false;
            for (int bi = 0; bi < bucketLength; bi++) {
                candidate[bi] = (min < currentBucketEnd[bi]);
                anyCandidates |= candidate[bi];
            }

            if (anyCandidates) {
                MappeableContainer container = bitmap.highLowContainer.getContainerAtIndex(pos);
                int max = min + (1 << 16);
                boolean[] bucketContainsPos = new boolean[bucketLength];
                boolean allContainPos = true;
                boolean anyContainPos = false;
                for (int bi = 0; bi < bucketLength; bi++) {
                    bucketContainsPos[bi] = (currentBucketStart[bi] <= min && max <= currentBucketEnd[bi]);
                    allContainPos &= bucketContainsPos[bi];
                    anyContainPos |= bucketContainsPos[bi];
                }

                if (anyContainPos) {
                    int cardinality = container.getCardinality();
                    for (int bi = 0; bi < bucketLength; bi++) {
                        if (bucketContainsPos[bi]) {
                            //System.out.println("BucketContainsPos");
                            buckets[bi][currentBucket[bi]] += cardinality;
                        }
                    }
                }

                if (!allContainPos) {
                    if (container instanceof MappeableArrayContainer) {
                        //System.out.println("ArrayContainer");
                        MappeableArrayContainer arrayContainer = (MappeableArrayContainer) container;
                        for (int i = 0; i < arrayContainer.cardinality && numExhausted < bucketLength; i++) {
                            int index = BufferUtil.toIntUnsigned(arrayContainer.content.get(i)) | min;
                            next:
                            for (int bi = 0; bi < bucketLength; bi++) {
                                if (!candidate[bi] || bucketContainsPos[bi] || exhausted[bi]) {
                                    continue;
                                }
                                while (index >= currentBucketEnd[bi]) {
                                    //System.out.println("Advance2 index:" + index + " >= currentBucketEnd:" + currentBucketEnd);
                                    currentBucket[bi]++;
                                    if (currentBucket[bi] == buckets[bi].length) {
                                        numExhausted++;
                                        exhausted[bi] = true;
                                        continue next;
                                    }
                                    currentBucketStart[bi] = indexes[bi][currentBucket[bi]];
                                    currentBucketEnd[bi] = indexes[bi][currentBucket[bi] + 1];
                                }
                                if (index >= currentBucketStart[bi]) {
                                    buckets[bi][currentBucket[bi]]++;
                                }
                            }

                        }
                    } else if (container instanceof MappeableRunContainer) {
                        MappeableRunContainer runContainer = (MappeableRunContainer) container;
                        for (int i = 0; i < runContainer.nbrruns && numExhausted < bucketLength; i++) {
                            int maxlength = BufferUtil.toIntUnsigned(runContainer.getLength(i));
                            int base = BufferUtil.toIntUnsigned(runContainer.getValue(i));
                            int index = (maxlength + base) | min;
                            next:
                            for (int bi = 0; bi < bucketLength; bi++) {
                                if (!candidate[bi] || bucketContainsPos[bi] || exhausted[bi]) {
                                    continue;
                                }
                                while (index >= currentBucketEnd[bi]) {
                                    //System.out.println("Advance3 index:" + index + " >= currentBucketEnd:" + currentBucketEnd);
                                    currentBucket[bi]++;
                                    if (currentBucket[bi] == buckets[bi].length) {
                                        numExhausted++;
                                        exhausted[bi] = true;
                                        continue next;
                                    }
                                    currentBucketStart[bi] = indexes[bi][currentBucket[bi]];
                                    currentBucketEnd[bi] = indexes[bi][currentBucket[bi] + 1];
                                }
                                if (index >= currentBucketStart[bi]) {
                                    buckets[bi][currentBucket[bi]]++;
                                }
                            }
                        }
                    } else {
                        //System.out.println("BitmapContainer");
                        MappeableBitmapContainer bitmapContainer = (MappeableBitmapContainer) container;
                        // nextSetBit no longer performs a bounds check
                        int maxIndex = bitmapContainer.bitmap.limit() << 6;
                        for (int i = bitmapContainer.nextSetBit(0);
                             i >= 0 && numExhausted < bucketLength;
                             i = (i + 1 >= maxIndex) ? -1 : bitmapContainer.nextSetBit(i + 1)) {
                            int index = BufferUtil.toIntUnsigned((short) i) | min;
                            next:
                            for (int bi = 0; bi < bucketLength; bi++) {
                                if (!candidate[bi] || bucketContainsPos[bi] || exhausted[bi]) {
                                    continue;
                                }
                                while (index >= currentBucketEnd[bi]) {
                                    //System.out.println("Advance3 index:" + index + " >= currentBucketEnd:" + currentBucketEnd);
                                    currentBucket[bi]++;
                                    if (currentBucket[bi] == buckets[bi].length) {
                                        numExhausted++;
                                        exhausted[bi] = true;
                                        continue next;
                                    }
                                    currentBucketStart[bi] = indexes[bi][currentBucket[bi]];
                                    currentBucketEnd[bi] = indexes[bi][currentBucket[bi] + 1];
                                }
                                if (index >= currentBucketStart[bi]) {
                                    buckets[bi][currentBucket[bi]]++;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static int containerMin(ImmutableRoaringBitmap bitmap, int pos) {
        return BufferUtil.toIntUnsigned(bitmap.highLowContainer.getKeyAtIndex(pos)) << 16;
    }

    private RoaringBufferInspection() {
    }
}
