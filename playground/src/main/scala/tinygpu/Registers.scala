package tinygpu

import chisel3._
import chisel3.util._

class Registers(val THREADS_PER_BLOCK: Int = 4, val THREAD_ID: Int = 0, val DATA_BITS: Int = 8) extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val block_id = Input(UInt(8.W))
    val core_state = Input(UInt(3.W))
    val decoded_rd_address = Input(UInt(4.W))
    val decoded_rs_address = Input(UInt(4.W))
    val decoded_rt_address = Input(UInt(4.W))
    val decoded_reg_write_enable = Input(Bool())
    val decoded_reg_input_mux = Input(UInt(2.W))
    val decoded_immediate = Input(UInt(DATA_BITS.W))
    val alu_out = Input(UInt(DATA_BITS.W))
    val lsu_out = Input(UInt(DATA_BITS.W))
    val rs = Output(UInt(8.W))
    val rt = Output(UInt(8.W))
  })

  // 16 registers per thread (13 free, 3 read-only)
  val registers = RegInit(VecInit(Seq.tabulate(16) {
    case 13 => 0.U(8.W) // %blockIdx
    case 14 => THREADS_PER_BLOCK.U(8.W) // %blockDim
    case 15 => THREAD_ID.U(8.W) // %threadIdx
    case _  => 0.U(8.W)
  }))

  val rsReg = RegInit(0.U(8.W))
  val rtReg = RegInit(0.U(8.W))

  io.rs := rsReg
  io.rt := rtReg

  val ARITHMETIC = 0.U(2.W)
  val MEMORY     = 1.U(2.W)
  val CONSTANT   = 2.U(2.W)

  when (reset.asBool) {
    rsReg := 0.U
    rtReg := 0.U
    for (i <- 0 until 13) { registers(i) := 0.U }
    registers(13) := 0.U // %blockIdx
    registers(14) := THREADS_PER_BLOCK.U
    registers(15) := THREAD_ID.U
  } .elsewhen (io.enable) {
    // [Bad Solution] Shouldn't need to set this every cycle, but matches SV
    registers(13) := io.block_id

    // Fill rs/rt when core_state = REQUEST
    when (io.core_state === "b011".U) {
      rsReg := registers(io.decoded_rs_address)
      rtReg := registers(io.decoded_rt_address)
    }

    // Store rd when core_state = UPDATE
    when (io.core_state === "b110".U) {
      when (io.decoded_reg_write_enable && io.decoded_rd_address < 13.U) {
        switch (io.decoded_reg_input_mux) {
          is (ARITHMETIC) { registers(io.decoded_rd_address) := io.alu_out }
          is (MEMORY)     { registers(io.decoded_rd_address) := io.lsu_out }
          is (CONSTANT)   { registers(io.decoded_rd_address) := io.decoded_immediate }
        }
      }
    }
  }
}