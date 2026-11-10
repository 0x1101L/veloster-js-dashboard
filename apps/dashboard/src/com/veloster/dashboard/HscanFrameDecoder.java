package com.veloster.dashboard;

/** Validated HSCAN framing with separate JSN and PD-baseline JS allowlists. */
final class HscanFrameDecoder {
    interface Sink { void record(int function, byte[] data, long receivedAt); }
    private static final int[] HEADER = {255, 11, 142, 1, 139, 199, 1, 0};
    private final byte[] frame = new byte[264];
    private final Sink sink;
    private final HscanProfile.Kind profile;
    private int used;
    HscanFrameDecoder(Sink sink) { this(sink,HscanProfile.Kind.JSN); }
    HscanFrameDecoder(Sink sink,HscanProfile.Kind profile) { this.sink = sink; this.profile=profile; }
    void reset() { used = 0; }

    void accept(byte[] data, int offset, int length, long receivedAt) {
        if (offset < 0 || length < 0 || offset > data.length - length) throw new IndexOutOfBoundsException();
        for (int i = offset; i < offset + length; i++) {
            frame[used++] = data[i];
            while (used > 0 && !prefix()) discardFirst();
            if (used != frame.length) continue;
            int checksum = 0;
            for (byte b : frame) checksum ^= b & 255;
            if (checksum == 0) {
                batch(receivedAt);
                used = 0;
            } else {
                discardFirst();
                while (used > 0 && !prefix()) discardFirst();
            }
        }
    }

    private boolean prefix() {
        for (int i = 0; i < Math.min(used, HEADER.length); i++)
            if ((frame[i] & 255) != HEADER[i]) return false;
        return true;
    }
    private void discardFirst() { used--; System.arraycopy(frame, 1, frame, 0, used); }

    private void batch(long receivedAt) {
        if ((frame[8] & 255) != 0xc7) return;
        int count = (frame[9] & 255) | ((frame[10] & 255) << 8);
        if (count > 84) return;
        int offset = 11;
        // Validate the complete batch before publishing any values.
        for (int i = 0; i < count; i++) {
            if (offset + 2 > 263) return;
            int size = frame[offset + 1] & 255;
            if (size < 1 || size > 8 || offset + 2 + size > 263) return;
            offset += 2 + size;
        }
        for (int i = offset; i < 263; i++) if (frame[i] != 0) return;
        offset = 11;
        for (int i = 0; i < count; i++) {
            int function = frame[offset++] & 255, size = frame[offset++] & 255;
            boolean accepted;
            if(profile==HscanProfile.Kind.JS||profile==HscanProfile.Kind.PD){
                accepted=(size==8&&(function==0||function==8||function==9||function==0x0a||function==0x0b
                        ||function==0x0d||function==0x13||function==0x14||function==0x1d||function==0x1e))
                    ||((function==2||function==0x1c)&&size==4)||(function==0x19&&size==5)
                    ||(function==0x20&&size==6);
            }else{
                accepted=((function==7||function==0x23||function==0x5a||function==0x6e)&&size==8)
                    ||(function==0x53&&size==5);
            }
            if (accepted) {
                byte[] value = new byte[size]; System.arraycopy(frame, offset, value, 0, size);
                sink.record(function, value, receivedAt);
            }
            offset += size;
        }
    }
}
