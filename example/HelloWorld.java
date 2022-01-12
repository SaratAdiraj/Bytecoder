package bytecodertest;

import de.mirkosertic.bytecoder.api.Import;
import de.mirkosertic.bytecoder.api.web.*;

public class HelloWorld {
    @Import(module = "world", name = "print_array")
    public static native void printArray(int[] aValue);

    public static void main(String[] args) {
        int[] myArray = { 1, 2, 3, 4, 5 };
        IntArray arr = OpaqueArrays.createIntArray(myArray.length);
        arr.setIntValue(1, 99);

        printArray(myArray);
        System.out.println("Hello World!");
    }
}
