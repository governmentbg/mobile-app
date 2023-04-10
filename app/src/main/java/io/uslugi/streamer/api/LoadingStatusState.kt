//
// Created by Atanas Doychinov on 24.10.22.
// Copyright (c) 2022 "Информационно обслужване" АД. All rights reserved.
//

package io.uslugi.streamer.api

sealed class LoadingStatusState {
    object START : LoadingStatusState()
    object LOADING : LoadingStatusState()
    object SUCCESS : LoadingStatusState()

    data class FAILURE(val exception: Throwable) : LoadingStatusState()
}