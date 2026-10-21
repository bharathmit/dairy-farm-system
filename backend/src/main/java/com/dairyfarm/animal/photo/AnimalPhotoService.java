package com.dairyfarm.animal.photo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.common.ApiException;

@Service
public class AnimalPhotoService {

    static final int MAX_PHOTOS = 3;
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final AnimalRepository animals;
    private final PhotoStorage storage;

    public AnimalPhotoService(AnimalRepository animals, PhotoStorage storage) {
        this.animals = animals;
        this.storage = storage;
    }

    /** Stores the photo and adds it to the animal. Returns the new photo's id (its file name). */
    @Transactional
    public String add(Long farmId, Long animalId, MultipartFile file) {
        Animal a = load(farmId, animalId);
        if (a.getPhotoPaths().size() >= MAX_PHOTOS) {
            throw ApiException.conflict("An animal can have up to " + MAX_PHOTOS + " photos. Remove one first.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a photo to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Photos can be up to 5 MB.");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Couldn't read that file. Try again.");
        }
        ImageType type = ImageType.detect(data)
                .orElseThrow(() -> ApiException.badRequest("Use a JPG, PNG or WebP photo."));
        String id = UUID.randomUUID() + "." + type.extension;
        String path = "farm-" + farmId + "/animal-" + animalId + "/" + id;
        storage.upload(path, data, type.contentType);

        List<String> paths = new ArrayList<>(a.getPhotoPaths());
        paths.add(path);
        a.setPhotoPaths(paths);
        return id;
    }

    @Transactional
    public void remove(Long farmId, Long animalId, String photoId) {
        Animal a = load(farmId, animalId);
        String path = a.getPhotoPaths().stream().filter(p -> p.endsWith("/" + photoId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Photo not found."));
        List<String> paths = new ArrayList<>(a.getPhotoPaths());
        paths.remove(path);
        a.setPhotoPaths(paths);
        storage.delete(List.of(path));
    }

    private Animal load(Long farmId, Long animalId) {
        return animals.findByIdAndFarmId(animalId, farmId)
                .orElseThrow(() -> ApiException.notFound("Animal not found."));
    }
}
