package tinygpu

import chisel3._
import chisel3.util._

class Scheduler(val THREADS_PER_BLOCK: Int = 4) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    // val reset = Input(Bool())
    // Control Signals
    val decoded_mem_read_enable = Input(Bool())
    val decoded_mem_write_enable = Input(Bool())
    val decoded_ret = Input(Bool())
    // Memory Access State
    val fetcher_state = Input(UInt(3.W))
    val lsu_state = Input(Vec(THREADS_PER_BLOCK, UInt(2.W)))
    // Current & Next PC
    val current_pc = Output(UInt(8.W))
    val next_pc = Input(Vec(THREADS_PER_BLOCK, UInt(8.W)))
    // Execution State
    val core_state = Output(UInt(3.W))
    val done = Output(Bool())
  })

  // 状态定义
  val IDLE    = 0.U(3.W)
  val FETCH   = 1.U(3.W)
  val DECODE  = 2.U(3.W)
  val REQUEST = 3.U(3.W)
  val WAIT    = 4.U(3.W)
  val EXECUTE = 5.U(3.W)
  val UPDATE  = 6.U(3.W)
  val DONE    = 7.U(3.W)

  val core_state = RegInit(IDLE)
  val current_pc = RegInit(0.U(8.W))
  val done = RegInit(false.B)

  io.core_state := core_state
  io.current_pc := current_pc
  io.done := done

  switch(core_state) {
    is (IDLE) {
      when (io.start) {
        core_state := FETCH
      }
    }
    is (FETCH) {
      when (io.fetcher_state === 2.U) { // FETCHED
        core_state := DECODE
      }
    }
    is (DECODE) {
      core_state := REQUEST
    }
    is (REQUEST) {
      core_state := WAIT
    }
    is (WAIT) {
      // Wait for all LSUs to finish their request before continuing
      val any_lsu_waiting = io.lsu_state.map(s => (s === 1.U) || (s === 2.U)).reduce(_ || _)
      when (!any_lsu_waiting) {
        core_state := EXECUTE
      }
    }
    is (EXECUTE) {
      core_state := UPDATE
    }
    is (UPDATE) {
      when (io.decoded_ret) {
        done := true.B
        core_state := DONE
      } .otherwise {
        // TODO: Branch divergence. For now assume all next_pc converge
        current_pc := io.next_pc(THREADS_PER_BLOCK-1)
        core_state := FETCH
      }
    }
    is (DONE) {
      // no-op
    }
  }

  when (reset.asBool) {
    current_pc := 0.U
    core_state := IDLE
    done := false.B
  }
}