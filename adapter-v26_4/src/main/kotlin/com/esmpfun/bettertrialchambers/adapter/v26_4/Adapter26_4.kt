package com.esmpfun.bettertrialchambers.adapter.v26_4

import com.esmpfun.bettertrialchambers.adapter.v26_3.Adapter26_3

/**
 * 26.4: the same as 26.3 so far. Wave mobs changing type in powder snow is handled in the
 * shared code (WaveMobTransformListener), since that event exists on every version.
 *
 * TODO(26.4): build against the Paper 26.4 API once it is published (-Ppaper264Api) and
 * move anything that differs here, such as a dispenser or pot change in ProtectionListener.
 */
class Adapter26_4 : Adapter26_3() {
    override val versions = "26.4"
}
