package io.uslugi.streamer.ui;

import io.uslugi.libcommon.CameraInfo;
import com.wmspanel.libstream.FocusMode;
import io.uslugi.streamer.cameramanager.CameraListHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetupHolder {
    private static final SetupHolder holder = new SetupHolder();

    public static SetupHolder getInstance() {
        return holder;
    }

    private final Map<String, LensSetup> settings = new HashMap<>();

    public LensSetup getLensSetup(String id, String physicalId) {
        return settings.get(CameraListHelper.uuid(id, physicalId));
    }

    public void init(final List<CameraInfo> cameraList, FocusMode initialSetup) {
        final Map<String, CameraInfo> map = CameraListHelper.toMap(cameraList, true);

        settings.clear();

        for (String uuid : map.keySet()) {
            settings.put(uuid, new LensSetup(
                    map.get(uuid).minimumFocusDistance > 0,
                    initialSetup));
        }
    }
}
