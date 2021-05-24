package blocktimsort;

import java.lang.reflect.Array;
import java.util.Comparator;

/**
 * @author Yuri-chan2007
 * @author Scandum
 * @author Control55
 *
 */
public final class BlockTimSort<K> {
    private Comparator<K> cmp;

    static final int GRAIL_STATIC_EXT_BUFFER_LEN = 512;

    private K[] extBuffer;
    private int extBufferLen;

    private int currBlockLen;
    private Subarray currBlockOrigin;

    public BlockTimSort(Comparator<K> cmp) {
        this.cmp = cmp;
    }

    private static <K> void write(K[] array, int at, K equals) {
        array[at] = equals;
    }

    private static <K> void grailSwap(K[] array, int a, int b) {
        K temp = array[a];
        array[a] = array[b];
        array[b] = temp;
    }

    private static <K> void grailBlockSwap(K[] array, int a, int b, int blockLen) {
        for (int i = 0; i < blockLen; i++) {
            grailSwap(array, a + i, b + i);
        }
    }

    /**
     * @param <K>
     * @param array
     * @param start
     * @param leftLen
     * @param rightLen
     * @implNote This rotation algorithm uses Cycle Reverse rotations (special
     *           thanks to Scandum and Control)
     */
    private static <K> void grailRotate(K[] array, int start, int leftLen, int rightLen) {
        int a = start, b = start + leftLen - 1, c = start + leftLen, d = start + leftLen + rightLen - 1;
        K swap;
        while (a < b && c < d) {
            swap = array[b];
            write(array, b--, array[a]);
            write(array, a++, array[c]);
            write(array, c++, array[d]);
            write(array, d--, swap);
        }
        while (a < b) {
            swap = array[b];
            write(array, b--, array[a]);
            write(array, a++, array[d]);
            write(array, d--, swap);
        }
        while (c < d) {
            swap = array[c];
            write(array, c++, array[d]);
            write(array, d--, array[a]);
            write(array, a++, swap);
        }
        while (a < d) {
            swap = array[a];
            write(array, a++, array[d]);
            write(array, d--, swap);
        }
    }

    private static <K> void grailInsertSort(K[] array, int start, int length, Comparator<K> cmp) {
        for (int item = 1; item < length; item++) {
            int left = start + item - 1;
            int right = start + item;

            while (left >= start && cmp.compare(array[left], array[right]) > 0) {
                grailSwap(array, left, right);
                left--;
                right--;
            }
        }
    }

    private static <K> int grailBinarySearchLeft(K[] array, int start, int length, K target, Comparator<K> cmp) {
        int left = 0;
        int right = length;

        while (left < right) {
            // equivalent to (left + right) / 2 with added overflow protection
            int middle = left + ((right - left) / 2);

            if (cmp.compare(array[start + middle], target) < 0) {
                left = middle + 1;
            } else {
                right = middle;
            }
        }
        return left;
    }

    // Credit to Anonymous0726 for debugging
    private static <K> int grailBinarySearchRight(K[] array, int start, int length, K target, Comparator<K> cmp) {
        int left = 0;
        int right = length;

        while (left < right) {
            // equivalent to (left + right) / 2 with added overflow protection
            int middle = left + ((right - left) / 2);

            if (cmp.compare(array[start + middle], target) > 0) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }
        return right;
    }

    // cost: 2 * length + idealKeys^2 / 2
    private static <K> int grailCollectKeys(K[] array, int start, int length, int idealKeys, Comparator<K> cmp) {
        int keysFound = 1; // by itself, the first item in the array is our first unique key
        int firstKey = 0; // the first item in the array is at the first position in the array
        int currKey = 1; // the index used for finding potentially unique items ("keys") in the array

        while (currKey < length && keysFound < idealKeys) {

            // Find the location in the key-buffer where our current key can be inserted in
            // sorted order.
            // If the key at insertPos is equal to currKey, then currKey isn't unique and we
            // move on.
            int insertPos = grailBinarySearchLeft(array, start + firstKey, keysFound, array[start + currKey], cmp);

            // The second part of this conditional does the equal check we were just talking
            // about; however,
            // if currKey is larger than everything in the key-buffer (meaning insertPos ==
            // keysFound),
            // then that also tells us it wasn't equal to anything in the key-buffer.
            if (insertPos == keysFound
                    || cmp.compare(array[start + currKey], array[start + firstKey + insertPos]) != 0) {

                // Rotate the key-buffer over to currKey's immediate left...
                // (this helps save a ton of swaps/writes.)
                grailRotate(array, start + firstKey, keysFound, currKey - (firstKey + keysFound));

                firstKey = currKey - keysFound;

                // Insert currKey to its spot in the key-buffer.
                grailRotate(array, start + firstKey + insertPos, keysFound - insertPos, 1);

                keysFound++;
            }
            // Test the next key
            currKey++;
        }

        // Bring however many keys we found back to the beginning of our array,
        // and return the number of keys collected.
        grailRotate(array, start, firstKey, keysFound);
        return keysFound;
    }

    private static <K> void grailPairwiseSwaps(K[] array, int start, int length, Comparator<K> cmp) {
        int index;
        for (index = 1; index < length; index += 2) {
            int left = start + index - 1;
            int right = start + index;

            if (cmp.compare(array[left], array[right]) > 0) {
                grailSwap(array, left - 2, right);
                grailSwap(array, right - 2, left);
            } else {
                grailSwap(array, left - 2, left);
                grailSwap(array, right - 2, right);
            }
        }

        int left = start + index - 1;
        if (left < start + length) {
            grailSwap(array, left - 2, left);
        }
    }

    private static <K> void grailPairwiseWrites(K[] array, int start, int length, Comparator<K> cmp) {
        int index;
        for (index = 1; index < length; index += 2) {
            int left = start + index - 1;
            int right = start + index;

            if (cmp.compare(array[left], array[right]) > 0) {
                // array[ left - 2] = array[right];
                write(array, left - 2, array[right]);
                // array[right - 2] = array[ left];
                write(array, right - 2, array[left]);
            } else {
                // array[ left - 2] = array[ left];
                write(array, left - 2, array[left]);
                // array[right - 2] = array[right];
                write(array, right - 2, array[right]);
            }
        }

        int left = start + index - 1;
        if (left < start + length) {
            // array[left - 2] = array[left];
            write(array, left - 2, array[left]);
        }
    }
}
