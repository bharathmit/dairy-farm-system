package com.dairyfarm.support;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.dairyfarm.animal.photo.PhotoStorage;

/** Stands in for Supabase Storage in tests. */
public class InMemoryPhotoStorage implements PhotoStorage {

    public final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public void upload(String path, byte[] data, String contentType) {
        files.put(path, data);
    }

    @Override
    public void delete(Collection<String> paths) {
        paths.forEach(files::remove);
    }

    @Override
    public Map<String, String> signedUrls(Collection<String> paths) {
        Map<String, String> urls = new HashMap<>();
        for (String p : paths) {
            urls.put(p, "https://storage.test/" + p + "?token=t");
        }
        return urls;
    }
}
