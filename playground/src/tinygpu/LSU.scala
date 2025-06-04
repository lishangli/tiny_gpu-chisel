package tinygpu

import chisel3._
import chisel3.util._

class LSU extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val core_state = Input(UInt(3.W))
    val decoded_mem_read_enable = Input(Bool())
    val decoded_mem_write_enable = Input(Bool())
    val rs = Input(UInt(8.W))
    val rt = Input(UInt(8.W))
    // Data Memory
    val mem_read_valid = Output(Bool())
    val mem_read_address = Output(UInt(8.W))
    val mem_read_ready = Input(Bool())
    val mem_read_data = Input(UInt(8.W))
    val mem_write_valid = Output(Bool())
    val mem_write_address = Output(UInt(8.W))
    val mem_write_data = Output(UInt(8.W))
    val mem_write_ready = Input(Bool())
    // LSU Outputs
    val lsu_state = Output(UInt(2.W))
    val lsu_out = Output(UInt(8.W))
  })

  // 状态定义
  val IDLE        = 0.U(2.W)
  val REQUESTING  = 1.U(2.W)
  val WAITING     = 2.U(2.W)
  val DONE        = 3.U(2.W)

  val lsu_state = RegInit(IDLE)
  val lsu_out = RegInit(0.U(8.W))
  val mem_read_valid = RegInit(false.B)
  val mem_read_address = RegInit(0.U(8.W))
  val mem_write_valid = RegInit(false.B)
  val mem_write_address = RegInit(0.U(8.W))
  val mem_write_data = RegInit(0.U(8.W))

  io.lsu_state := lsu_state
  io.lsu_out := lsu_out
  io.mem_read_valid := mem_read_valid
  io.mem_read_address := mem_read_address
  io.mem_write_valid := mem_write_valid
  io.mem_write_address := mem_write_address
  io.mem_write_data := mem_write_data

  when (reset.asBool) {
    lsu_state := IDLE
    lsu_out := 0.U
    mem_read_valid := false.B
    mem_read_address := 0.U
    mem_write_valid := false.B
    mem_write_address := 0.U
    mem_write_data := 0.U
  } .elsewhen (io.enable) {
    // Memory read (LDR)
    when (io.decoded_mem_read_enable) {
      switch (lsu_state) {
        is (IDLE) {
          when (io.core_state === "b011".U) {
            lsu_state := REQUESTING
          }
        }
        is (REQUESTING) {
          mem_read_valid := true.B
          mem_read_address := io.rs
          lsu_state := WAITING
        }
        is (WAITING) {
          when (io.mem_read_ready) {
            mem_read_valid := false.B
            lsu_out := io.mem_read_data
            lsu_state := DONE
          }
        }
        is (DONE) {
          when (io.core_state === "b110".U) {
            lsu_state := IDLE
          }
        }
      }
    }

    // Memory write (STR)
    when (io.decoded_mem_write_enable) {
      switch (lsu_state) {
        is (IDLE) {
          when (io.core_state === "b011".U) {
            lsu_state := REQUESTING
          }
        }
        is (REQUESTING) {
          mem_write_valid := true.B
          mem_write_address := io.rs
          mem_write_data := io.rt
          lsu_state := WAITING
        }
        is (WAITING) {
          when (io.mem_write_ready) {
            mem_write_valid := false.B
            lsu_state := DONE
          }
        }
        is (DONE) {
          when (io.core_state === "b110".U) {
            lsu_state := IDLE
          }
        }
      }
    }
  }
}