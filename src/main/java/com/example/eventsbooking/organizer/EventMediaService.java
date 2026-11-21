package com.example.eventsbooking.organizer;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventMedia;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.MediaResponse;
import com.example.eventsbooking.storage.EventImageService;
import com.example.eventsbooking.storage.ImageType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo upload. The type of a file is decided by its content (the signature), never by the extension or the type the
 * client declared; the extension must additionally agree with the content, so a renamed file is refused. The whole
 * request is checked before anything is stored: it is all or nothing.
 */
@Service
@Transactional
public class EventMediaService {

    static final int MAX_IMAGES_PER_EVENT = 6;
    static final int MAX_BYTES = 8 * 1024 * 1024;

    private final EventRepository events;
    private final EventImageService images;

    public EventMediaService(EventRepository events, EventImageService images) {
        this.events = events;
        this.images = images;
    }

    /** Appends the images to the event (the first one ever uploaded is the cover) and returns their public URLs. */
    public List<MediaResponse> upload(long eventId, long organizerId, List<UploadedImage> files) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.getOrganizerId() != organizerId) {
            throw new AccessDeniedException("Event " + eventId + " belongs to another organizer");
        }
        if (files.isEmpty()) {
            throw new InvalidImageException("No files were uploaded");
        }
        int existing = event.getMedia().size();
        if (existing + files.size() > MAX_IMAGES_PER_EVENT) {
            throw new ConflictException("Too many images", "An event can have at most " + MAX_IMAGES_PER_EVENT
                    + " photos and this one has " + existing + " already");
        }

        List<ImageType> types = new ArrayList<>();
        for (UploadedImage file : files) {
            types.add(verify(file));
        }

        List<String> stored = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                stored.add(images.store(files.get(i).content(), types.get(i)));
            }
            images.attach(eventId, stored);
        } catch (RuntimeException e) {
            stored.forEach(images::discard);   // do not leave half an upload behind
            throw e;
        }

        List<EventMedia> added = stored.stream().map(name -> new EventMedia("/api/media/" + name, null)).toList();
        event.addMedia(added);
        event.bumpRevision();
        return added.stream().map(media -> new MediaResponse(media.url(), media.alt())).toList();
    }

    private static ImageType verify(UploadedImage file) {
        String name = file.filename() == null ? "the file" : "'" + file.filename() + "'";
        byte[] content = file.content();
        if (content == null || content.length == 0) {
            throw new InvalidImageException(name + " is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ImageTooLargeException();
        }
        ImageType real = ImageType.detect(content).orElseThrow(
                () -> new UnsupportedImageTypeException(name + " is not a JPEG, PNG or WebP image"));
        ImageType claimed = ImageType.fromFileName(file.filename()).orElseThrow(
                () -> new UnsupportedImageTypeException(name + " must have the extension .jpg, .jpeg, .png or .webp"));
        if (claimed != real) {
            throw new UnsupportedImageTypeException("The extension of " + name + " does not match its content");
        }
        return real;
    }
}
