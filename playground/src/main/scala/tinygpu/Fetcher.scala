package tinygpu

import chisel3._
import chisel3.util._

class Fetcher(val PROGRAM_MEM_ADDR_BITS: Int = 8, val PROGRAM_MEM_DATA_BITS: Int = 16) extends Module {
  val io = IO(new Bundle {
    // Execution State
    val core_state    = Input(UInt(3.W))
    val current_pc    = Input(UInt(8.W))
    // Program Memory
    val mem_read_valid   = Output(Bool())
    val mem_read_address = Output(UInt(PROGRAM_MEM_ADDR_BITS.W))
    val mem_read_ready   = Input(Bool())
    val mem_read_data    = Input(UInt(PROGRAM_MEM_DATA_BITS.W))
    // Fetcher Output
    val fetcher_state = Output(UInt(3.W))
    val instruction   = Output(UInt(PROGRAM_MEM_DATA_BITS.W))
  })

  // 状态定义
  val IDLE     = 0.U(3.W)
  val FETCHING = 1.U(3.W)
  val FETCHED  = 2.U(3.W)

  val fetcher_state = RegInit(IDLE)
  val mem_read_valid = RegInit(false.B)
  val mem_read_address = RegInit(0.U(PROGRAM_MEM_ADDR_BITS.W))
  val instruction = RegInit(0.U(PROGRAM_MEM_DATA_BITS.W))

  io.fetcher_state := fetcher_state
  io.mem_read_valid := mem_read_valid
  io.mem_read_address := mem_read_address
  io.instruction := instruction

  switch(fetcher_state) {
    is (IDLE) {
      // Start fetching when core_state = FETCH
      when (io.core_state === 1.U) {
        fetcher_state := FETCHING
        mem_read_valid := true.B
        mem_read_address := io.current_pc
      }
    }
    is (FETCHING) {
      // Wait for response from program memory
      when (io.mem_read_ready) {
        fetcher_state := FETCHED
        instruction := io.mem_read_data
        mem_read_valid := false.B
      }
    }
    is (FETCHED) {
      // Reset when core_state = DECODE
      when (io.core_state === 2.U) {
        fetcher_state := IDLE
      }
    }
  }

  when (reset.asBool) {
    fetcher_state := IDLE
    mem_read_valid := false.B
    mem_read_address := 0.U
    instruction := 0.U
  }
}