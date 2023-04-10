package io.uslugi.streamer.overlay;

public interface OverlayLoaderListener {
    void onImageLoadComplete(OverlayLoader source);

    void onImageLoaded(String name);

    void onLoadError(String name, String error);
}
