package cn.rismd.openncb

import chisel3._
import chisel3.util.experimental.forceName
import circt.stage.ChiselStage
import org.chipsalliance.cde.config.Config
import cn.rismd.openncb.axi.AXI4Parameters
import cn.rismd.openncb.axi.AXI4ParametersKey
import cn.rismd.openncb.axi.intf.AXI4InterfaceMaster
import cn.rismd.openncb.chi.CHIParameters
import cn.rismd.openncb.chi.CHIParametersKey
import cn.rismd.openncb.chi.EnumCHIIssue
import cn.rismd.openncb.chi.intf._


class NCB200VTop extends Module {

    // Configurations
    implicit val p = new Config((_, _, _) => {

        case AXI4ParametersKey  => new AXI4Parameters(
            idWidth             = 4,
            addrWidth           = 64,
            dataWidth           = 64
        )

        case CHIParametersKey   => new CHIParameters(
            issue               = EnumCHIIssue.B,
            nodeIdWidth         = 7,
            reqAddrWidth        = 48,
            reqRsvdcWidth       = 4,
            datRsvdcWidth       = 4,
            dataWidth           = 256,
            dataCheckPresent    = false,
            poisonPresent       = false,
            mpamPresent         = false
        )

        case NCBParametersKey   => new NCBParameters(
            axiMasterOrder      = EnumAXIMasterOrder.WriteAddress 
        )
    })


    // I/O
    val io = IO(new Bundle {
        val chi                     = CHISNFRawInterface()
        val axi                     = AXI4InterfaceMaster()
    })

    // Module: NCB200
    val uNCB200     = Module(new NCB200)

    io.chi <> uNCB200.io.chi
    io.axi <> uNCB200.io.axi
}

// Generate to SystemVerilog
object NCB200VTop extends App {

    ChiselStage.emitSystemVerilogFile(
        new NCB200VTop,
        Array("--target-dir", "build")
    )
}
