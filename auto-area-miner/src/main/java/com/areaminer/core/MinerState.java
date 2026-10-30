package com.areaminer.core;

public enum MinerState {
    IDLE,
    SCANNING,
    PLANNING,
    MOVING,       // target is out of reach - walk closer
    POSITIONING,  // in reach, crosshair is not on the target
    MINING,       // crosshair is on the target - mine it
    VERIFYING,    // centre broke, waiting for the server to confirm the rest
    RECOVERING,   // retry / waiting for chunks
    PAUSED,
    COMPLETED,
    ERROR
}
