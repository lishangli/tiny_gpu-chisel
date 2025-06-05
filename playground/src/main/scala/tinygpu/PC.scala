package tinygpu

import chisel3._
import chisel3.util._

class PC(val DATA_MEM_DATA_BITS: Int = 8, val PROGRAM_MEM_ADDR_BITS: Int = 8) extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val core_state = Input(UInt(3.W))
    val decoded_nzp = Input(UInt(3.W))
    val decoded_immediate = Input(UInt(DATA_MEM_DATA_BITS.W))
    val decoded_nzp_write_enable = Input(Bool())
    val decoded_pc_mux = Input(Bool())
    val alu_out = Input(UInt(DATA_MEM_DATA_BITS.W))
    val current_pc = Input(UInt(PROGRAM_MEM_ADDR_BITS.W))
    val next_pc = Output(UInt(PROGRAM_MEM_ADDR_BITS.W))
  })

  val nzp = RegInit(0.U(3.W))
  val next_pc = RegInit(0.U(PROGRAM_MEM_ADDR_BITS.W))

  io.next_pc := next_pc

  when (reset.asBool) {
    nzp := 0.U
    next_pc := 0.U
  } .elsewhen (io.enable) {
    // Update PC when core_state = EXECUTE
    when (io.core_state === "b101".U) {
      when (io.decoded_pc_mux) {
        when ((nzp & io.decoded_nzp) =/= 0.U) {
          // On BRnzp instruction, branch to immediate if NZP case matches previous CMP
          next_pc := io.decoded_immediate
        } .otherwise {
          // Otherwise, just update to PC + 1 (next line)
          next_pc := io.current_pc + 1.U
        }
      } .otherwise {
        // By default update to PC + 1 (next line)
        next_pc := io.current_pc + 1.U
      }
    }
    // Store NZP when core_state = UPDATE
    when (io.core_state === "b110".U) {
      when (io.decoded_nzp_write_enable) {
        nzp := io.alu_out(2,0)
      }
    }
  }
}