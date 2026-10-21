package com.dairyfarm.animal.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/** Skips the UTF-8 byte-order mark that Excel puts at the start of "CSV UTF-8" files. */
final class BomStripper extends PushbackInputStream {

    BomStripper(InputStream in) throws IOException {
        super(in, 3);
        byte[] head = new byte[3];
        int n = read(head, 0, 3);
        boolean bom = n == 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF;
        if (!bom && n > 0) {
            unread(head, 0, n);
        }
    }
}
