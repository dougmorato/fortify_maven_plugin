package com.fortify.ps.maven.plugin.sca;

import java.io.*;

public class DirectoryWalker {
    private File dir;

    public DirectoryWalker(File dir) {
        this.dir = dir;
    }

    @SuppressWarnings("unchecked")
    public <T> T walk(FileProcessor<T> processor) {
        try {
            walk(dir, processor);

            // At this point, we have processed all files recursively. The condition that would cause an early exit did
            // not occur, so we just return the fall-back value.
            //
            return processor.getDefaultValue();
        } catch (DoneException ee) {
            return (T)ee.data;
        }
    }

    private <T> void walk(File path, FileProcessor<T> processor) throws DoneException {
        if (path != null && path.isDirectory()) {
            File[] children = path.listFiles();
            for (File child : children) {
                processor.process(child);
                walk(child, processor);
            }
        }
    }

    // Unfortunately, due to generics being implemented using type erasure, generic types that extend java.lang.Throwable
    // are a compile-time error. I have to make do with good o' Object.
    //
    public static class DoneException extends Exception {
        public final Object data;

        public DoneException(Object data) {
            this.data = data;
        }
    }

    public abstract static class FileProcessor<T> {
        // When throwing an ExitException, only set data to something of type T or a ClassCastException will occur.
        //
        public abstract void process(File f) throws DoneException;

        // A default implementation is provided, because someone using this class may not always use the early-exit
        // capability.
        //
        public T getDefaultValue() { return null; }
    }
}
