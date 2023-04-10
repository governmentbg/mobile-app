package io.uslugi.streamer.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class ImageLayerConfig {
    @Id
    public long id;

    public String name;
    public int type;
    public String url;
    public String cacheUrl;
    public boolean active;
    public int zIndex;
    public float displaySize;
    public float displayPosX;
    public float displayPosY;
    public String httpEtag;
    public String httpLastModified;
    public int updateInterval;
}
