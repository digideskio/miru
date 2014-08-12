package com.googlecode.javaewah;

import java.util.Iterator;

/*
 * Copyright 2009-2013, Daniel Lemire, Cliff Moon, David McIntosh, Robert Becho, Google Inc., Veronika Zenz and Owen Kaser
 * Licensed under the Apache License, Version 2.0.
 */

/**
 * Convenience functions for working over iterators
 * 
 */
public class IteratorUtil {

        /**
         * @param i
         *                iterator we wish to iterate over
         * @return an iterator over the set bits corresponding to the iterator
         */
        public static com.googlecode.javaewah.IntIterator toSetBitsIntIterator(final IteratingRLW i) {
                return new com.googlecode.javaewah.IntIteratorOverIteratingRLW(i);
        }

        /**
         * @param i
         *                iterator we wish to iterate over
         * @return an iterator over the set bits corresponding to the iterator
         */
        public static Iterator<Integer> toSetBitsIterator(final IteratingRLW i) {
                return new Iterator<Integer>() {
                        @Override
                        public boolean hasNext() {
                                return this.under.hasNext();
                        }

                        @Override
                        public Integer next() {
                                return new Integer(this.under.next());
                        }

                        @Override
                        public void remove() {
                        }

                        final private com.googlecode.javaewah.IntIterator under = toSetBitsIntIterator(i);
                };

        }

        /**
         * Generate a bitmap from an iterator
         * 
         * @param i
         *                iterator we wish to materialize
         * @param c
         *                where we write
         */
        public static void materialize(final IteratingRLW i,
                final BitmapStorage c) {
                while (true) {
                        if (i.getRunningLength() > 0) {
                                c.addStreamOfEmptyWords(i.getRunningBit(),
                                        i.getRunningLength());
                        }
                        for (int k = 0; k < i.getNumberOfLiteralWords(); ++k)
                                c.addWord(i.getLiteralWordAt(k));
                        if (!i.next())
                                break;
                }
        }

        /**
         * @param i
         *                iterator we wish to iterate over
         * @return the cardinality (number of set bits) corresponding to the
         *         iterator
         */
        public static int cardinality(final IteratingRLW i) {
                int answer = 0;
                while (true) {
                        if (i.getRunningBit())
                                answer += i.getRunningLength()
                                        * EWAHCompressedBitmap.wordinbits;
                        for (int k = 0; k < i.getNumberOfLiteralWords(); ++k)
                                answer += Long.bitCount(i.getLiteralWordAt(k));
                        if (!i.next())
                                break;
                }
                return answer;
        }

        /**
         * @param x
         *                set of bitmaps
         * @return an array of iterators corresponding to the array of bitmaps
         */
        public static IteratingRLW[] toIterators(
                final EWAHCompressedBitmap... x) {
                IteratingRLW[] X = new IteratingRLW[x.length];
                for (int k = 0; k < X.length; ++k) {
                        X[k] = new com.googlecode.javaewah.IteratingBufferedRunningLengthWord(x[k]);
                }
                return X;
        }

        /**
         * Turn an iterator into a bitmap.
         * 
         * @param i
         *                iterator we wish to materialize
         * @param c
         *                where we write
         * @param Max
         *                maximum number of words we wish to materialize
         * @return how many words were actually materialized
         */
        public static long materialize(final IteratingRLW i,
                final BitmapStorage c, long Max) {
                final long origMax = Max;
                while (true) {
                        if (i.getRunningLength() > 0) {
                                long L = i.getRunningLength();
                                if (L > Max)
                                        L = Max;
                                c.addStreamOfEmptyWords(i.getRunningBit(), L);
                                Max -= L;
                        }
                        long L = i.getNumberOfLiteralWords();
                        for (int k = 0; k < L; ++k)
                                c.addWord(i.getLiteralWordAt(k));
                        if (Max > 0) {
                                if (!i.next())
                                        break;
                        } else
                                break;
                }
                return origMax - Max;
        }

        /**
         * Turn an iterator into a bitmap
         * 
         * @param i
         *                iterator we wish to materialize
         * @return materialized version of the iterator
         */
        public static EWAHCompressedBitmap materialize(final IteratingRLW i) {
                EWAHCompressedBitmap ewah = new EWAHCompressedBitmap();
                materialize(i, ewah);
                return ewah;
        }

}
