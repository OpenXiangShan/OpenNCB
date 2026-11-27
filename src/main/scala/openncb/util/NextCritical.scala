package cc.xiangshan.openncb.util

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.isPow2
import chisel3.util.log2Ceil
import cc.xiangshan.openncb.axi.field.AXI4FieldAxBURST

/*
Suppose critical length = 8 (slots 0..7), and you have a 2-beat WRAP (len = 1, so group size g = 2).
The aligned groups of 2 slots are:

Group 0: slots {0, 1}
Group 1: slots {2, 3}
Group 2: slots {4, 5}
Group 3: slots {6, 7}

If the burst starts at chunk 1 (critical has bit 1 high):
Inside Group 0 ({0, 1}), rotating left by 1 moves the 1 from slot 1 to slot 0—not slot 2.

This is done by rotateInGroups()
*/

object NextCritical {

    def apply(critical: Vec[Bool], burst: UInt, len: UInt): Vec[Bool] = {

        val n       = critical.length
        val crit    = critical.asUInt

        def rotateInGroups(g: Int): UInt = VecInit((0 until n).map(i => {
            crit((i / g) * g + (i % g + g - 1) % g)
        })).asUInt

        val wrapNext = MuxLookup(len, crit.rotateLeft(1))(
            (1 to log2Ceil(n)).map(k => ((1 << k) - 1).U -> rotateInGroups(1 << k))
        )

        // If burst is WRAP, call wrapNext, else crit.rotateLeft(1) -> for INCR/FIXED
        VecInit(Mux(burst === AXI4FieldAxBURST.WRAP.U, wrapNext, crit.rotateLeft(1)).asBools)
    }
}
