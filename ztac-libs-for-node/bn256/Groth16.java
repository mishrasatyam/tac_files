package com.tac.ztac.bn256;

public class Groth16 {

    public static native boolean verify(byte[] vk, byte[] proof, byte[] inputs);

    static {
        new JNILibrary("ztac_jni", Groth16.class).load();
    }
}
