package com.dairyfarm.animal.photo;

import java.util.Collection;
import java.util.Map;

/** Where animal photos live (a private Supabase Storage bucket). */
public interface PhotoStorage {

    void upload(String path, byte[] data, String contentType);

    /** Best effort: missing files are ignored. */
    void delete(Collection<String> paths);

    /** Short-lived links for showing photos; empty when storage isn't set up. */
    Map<String, String> signedUrls(Collection<String> paths);
}
