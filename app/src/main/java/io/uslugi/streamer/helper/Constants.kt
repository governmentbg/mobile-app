package io.uslugi.streamer.helper

import android.media.MediaRecorder

object Constants {
    const val APP_NAME = "Видеонаблюдение"

    object HTTPConfig {
        const val BASE_URL = "BASE_URL"
        const val READ_TIMEOUT = 20L
        const val WRITE_TIMEOUT = 20L
        const val CONNECT_TIMEOUT = 20L
    }

    /**
     * All configurations related to
     * - Audio
     * - Video
     * - Recording
     * - Connection
     * - Overlays
     * - Advanced
     */
    object Config {
        object Audio {
            const val SOURCE = MediaRecorder.AudioSource.CAMCORDER
            const val USE_BLUETOOTH_MIC = false
            const val CHANNEL = 1 // Mono
            const val SAMPLE_RATE = 44100
            const val AUDIO_ONLY_CAPTURE = false
            const val GAIN = 10f // 10Db

            const val MICROPHONE_DIRECTION_FOLLOWS_CAMERA = false
            const val MICROPHONE_DIRECTION = -1 // System default (a.k.a none)
        }

        object Video {
            const val DEFAULT_CAMERA = "0" // Default camera id
            const val RESOLUTION_WIDTH = 1280
            const val RESOLUTION_HEIGHT = 720
            const val LIVE_ROTATION = false
            const val VIDEO_ORIENTATION = "0" // Landscape, use "1" for Portrait

            /**
             * Possible values
             * 0 -> OFF
             * 1 -> 50 Hz
             * 2 -> 60 Hz
             * 3 -> Auto
             */
            const val ANTI_FLICKER = 0 // OFF
            const val FOCUS_MODE = 0 // Continuous auto focus
            const val WHITE_BALANCE = 0 // Auto
            const val EXPOSURE_COMPENSATION = 0
            const val CONCURRENT_CAMERA_MODE = 0 // OFF

            const val BITRATE_MODE = -1 // System default
            const val BITRATE = 1500 * 1000 // Kbps -> bps

            const val KEYFRAME_FREQUENCY = 2 // Seconds

            const val FPS = 20F // Frames per second

            const val PROFILE = 2 // H.264 Profile Main

            const val ELECTRONIC_IMAGE_STABILIZATION = 1 // ON
            const val OPTICAL_IMAGE_STABILIZATION = 1 // ON
            const val NOISE_REDUCTION = 1 // Fast

            const val ADAPTIVE_BITRATE_STREAMING_MODE_VALUE = 2 // Ladder ascend
            const val ADAPTIVE_FRAME_RATE = true // Enabled
        }

        object Recording {
            const val IS_RECORDING_ON = true

            /**
             * Should the recorded video be split into separate videos
             *  - `IS_SPLIT_VIDEO_ENABLED` sets whether it's enabled
             *  - `SPLIT_VIDEO_DURATION` sets the duration of each vide
             */
            const val IS_SPLIT_VIDEO_ENABLED = false
            const val SPLIT_VIDEO_DURATION = 1 // Minutes. Depends on `IS_SPLIT_VIDEO_ENABLED

            const val LOG_TO_FILE = false
        }

        object Connection {
            const val MAX_CONNECTIONS = 3
            const val IDLE_TIMEOUT = 10 // Seconds
            const val RECONNECT_TIMEOUT = 3 // Seconds
            const val RECONNECT_TIMEOUT_NO_NETWORK = 10 // Seconds
        }

        object Display {
            const val SHOW_AUDIO_LEVEL_METER = true
            const val SHOW_HORIZON_LEVEL = false
        }

        object Overlays {
            const val SHOW_LAYERS_ON_PREVIEW = true
            const val STANDBY_LAYERS_ENABLED = false
        }

        object Advanced {

            // ENCODER BUFFER SIZE ⤵

            const val USE_CUSTOM_BUFFER_DURATION = false
            const val CIRCULAR_BUFFER_DURATION = 3000

            // CAMERA CONTROLS ⤵

            /**
             * Possible values here are:
             * 0 -> do nothing
             * 1 -> start/stop recording/stream
             * 2 -> zoom
             * 3 -> camera flip
             */
            const val VOLUME_KEYS_ACTION = 1 // start/stop recording/stream

            // PLATFORM-SPECIFIC ⤵

            /**
             * Even if network in not detected,
             * upon tapping "Start" the connection will be put into queue.
             */
            const val DO_NOT_CHECK_NETWORK_PRESENCE = false

            /**
             * Enable this to workaround for Google Nexus 6P (angler)
             * and Google Pixel (sailfish) camera flip issue.
             */
            const val CONVERT_BETWEEN_ANDROID_CAMERA_TIMESTAMP_AND_SYSTEM_TIME = false

            /**
             * Don't check whether a given video size (width and height)
             * is supported by an encoder.
             */
            const val ALLOW_ALL_CAMERA_RESOLUTIONS = false

            // ADVANCED ⤵

            // Say no to vertical videos
            const val HORIZON_STREAM_DEMO = false

            /**
             * Possible values
             * 0 -> Auto detect
             * 1 -> Camera
             * 2 -> Camera2
             */
            const val PREFERRED_CAMERA_API = 0 // Auto

            /**
             * Stream and record front camera appears in preview mirrored.
             */
            const val MIRROR_FRONT_CAMERA = false

            // BACKGROUND STREAMING ⤵

            /**
             * Allow notifications to display stream statistics
             * in the notifications drawer
             */
            const val KEEP_STREAMING_WHEN_NOT_IN_FOCUS = false
        }
    }

    object StringPlaceholders {
        const val EMPTY = ""
    }

    object Mode {
        const val TEST_SETUP = "test-setup"
        const val TEST_SIK = "test-sik"
        const val REAL = "real"
        const val UNKNOWN = "unknown"
    }

    object Extras {
        const val IS_TEST = "is-test"
    }

    object Response {
        const val OK = "OK"
    }

    object SharedPreferences {
        const val SIK_ENCRYPTED_SHARED_PREFERENCES = "SIK_ENCRYPTED_PREFERENCES"
    }

    object DelayTimes {
        const val START_RECORDING_TIME = 5000L
        const val STOP_RECORDING_TIME_TEST = 10000L
        const val RETRY_TIME_DELAY = 5000L
    }
}