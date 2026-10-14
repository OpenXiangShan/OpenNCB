package cc.xiangshan.openncb

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.chipsalliance.cde.config.{Config, Parameters}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cc.xiangshan.openncb.axi._
import cc.xiangshan.openncb.chi._
import cc.xiangshan.openncb.chi.field.CHIFieldRespErr
import cc.xiangshan.openncb.logical.NCBTransactionQueue

/*
* Directed test for the TXRSP operand read path of NCBTransactionQueue.
*
* The queue has two operand read ports, one for TXDAT and one for TXRSP. TXDAT's
* strobe only ever selects read entries; TXRSP's strobe selects the entry whose
* Comp/DBIDResp is being sent. NCBTransactionQueue used to drive the TXRSP operand
* read mux from TXDAT's strobe, so the RespErr of a write Comp came from a read
* entry (OK) or from nothing at all, and an AXI SLVERR/DECERR on B never reached
* the requester.
*
* The harness allocates one read entry (WriteRespErr = OK) and one write entry
* (WriteRespErr = NDERR), then lets the test drive the two strobes independently.
*/
object TXRSPStrobeHarness {
    val params: Parameters = new Config((_, _, _) => {

        case AXI4ParametersKey  => new AXI4Parameters(
            idWidth     = 4,
            addrWidth   = 64,
            dataWidth   = 64
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
            outstandingDepth    = 2,
            writeNoError        = false
        )
    })
}

class TXRSPStrobeHarness extends Module {

    implicit val p: Parameters = TXRSPStrobeHarness.params

    val queue = Module(new NCBTransactionQueue)

    val io = IO(new Bundle {
        val txrspSel        = Input(UInt(2.W))
        val txdatSel        = Input(UInt(2.W))
        val writeRespErr    = Output(chiselTypeOf(queue.io.upstreamTxRsp.operandRead.bits.WriteRespErr))
    })

    // Leave every queue input unconnected; the ports this test drives are set below.
    queue.io <> DontCare

    // Allocate entry 0 as a read entry, then entry 1 as a write entry.
    val counter     = RegInit(0.U(2.W))
    val allocating  = counter < 2.U

    queue.io.allocate.en    := allocating
    queue.io.allocate.strb  := VecInit(counter === 0.U, counter === 1.U)
    queue.io.allocate.bits  := 0.U.asTypeOf(queue.io.allocate.bits)

    queue.io.allocate.bits.op.axi.ReadData.valid        := counter === 0.U

    queue.io.allocate.bits.op.axi.WriteResponse.valid   := counter === 1.U
    queue.io.allocate.bits.op.chi.Comp.valid            := counter === 1.U

    queue.io.allocate.bits.operand.chi.WriteRespErr     := Mux(counter === 1.U,
        CHIFieldRespErr.RESPERR_NDERR.U, CHIFieldRespErr.RESPERR_OK.U)

    when (allocating) {
        counter := counter + 1.U
    }

    queue.io.upstreamTxRsp.operandRead.strb := io.txrspSel.asBools
    queue.io.upstreamTxDat.operandRead.strb := io.txdatSel.asBools

    io.writeRespErr := queue.io.upstreamTxRsp.operandRead.bits.WriteRespErr
}

class NCBTransactionQueueSpec extends AnyFlatSpec with Matchers {

    behavior of "NCBTransactionQueue"

    // Entry 1 is the write entry (NDERR), entry 0 is the read entry (OK).
    it should "read TXRSP operands with the TXRSP strobe" in {
        simulate(new TXRSPStrobeHarness) { dut =>
            dut.reset.poke(true.B)
            dut.clock.step(5)
            dut.reset.poke(false.B)
            dut.clock.step(3)   // allocate entry 0 and entry 1

            dut.io.txrspSel.poke(2.U)   // TXRSP sends entry 1's Comp
            dut.io.txdatSel.poke(1.U)   // TXDAT would read entry 0
            dut.clock.step(1)
            dut.io.writeRespErr.expect(CHIFieldRespErr.RESPERR_NDERR.U)
        }
    }

    // Negative control: the output must follow the TXRSP strobe in both directions.
    it should "not read TXRSP operands with the TXDAT strobe" in {
        simulate(new TXRSPStrobeHarness) { dut =>
            dut.reset.poke(true.B)
            dut.clock.step(5)
            dut.reset.poke(false.B)
            dut.clock.step(3)   // allocate entry 0 and entry 1

            dut.io.txrspSel.poke(1.U)   // TXRSP sends entry 0's response
            dut.io.txdatSel.poke(2.U)   // TXDAT would read entry 1
            dut.clock.step(1)
            dut.io.writeRespErr.expect(CHIFieldRespErr.RESPERR_OK.U)
        }
    }
}
