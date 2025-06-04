package tinygpu

import chisel3._
import chisel3.util._

class Dispatch(val NUM_CORES: Int = 2, val THREADS_PER_BLOCK: Int = 4) extends Module {
  val io = IO(new Bundle {
    val start         = Input(Bool())
    // val reset         = Input(Bool())
    // val clk           = Input(Clock()) // 可省略，Chisel自动有clock
    // Kernel Metadata
    val thread_count  = Input(UInt(8.W))
    // Core States
    val core_done     = Input(Vec(NUM_CORES, Bool()))
    val core_start    = Output(Vec(NUM_CORES, Bool()))
    val core_reset    = Output(Vec(NUM_CORES, Bool()))
    val core_block_id = Output(Vec(NUM_CORES, UInt(8.W)))
    val core_thread_count = Output(Vec(NUM_CORES, UInt((log2Ceil(THREADS_PER_BLOCK)+1).W)))
    // Kernel Execution
    val done          = Output(Bool())
  })

  // 总块数
  val total_blocks = Wire(UInt(8.W))
  total_blocks := (io.thread_count + (THREADS_PER_BLOCK.U - 1.U)) / THREADS_PER_BLOCK.U

  // 状态寄存器
  val blocks_dispatched = RegInit(0.U(8.W))
  val blocks_done       = RegInit(0.U(8.W))
  val start_execution   = RegInit(false.B)

  val core_start    = RegInit(VecInit(Seq.fill(NUM_CORES)(false.B)))
  val core_reset    = RegInit(VecInit(Seq.fill(NUM_CORES)(true.B)))
  val core_block_id = RegInit(VecInit(Seq.fill(NUM_CORES)(0.U(8.W))))
  val core_thread_count = RegInit(VecInit(Seq.fill(NUM_CORES)(THREADS_PER_BLOCK.U((log2Ceil(THREADS_PER_BLOCK)+1).W))))
  val done = RegInit(false.B)

  io.core_start := core_start
  io.core_reset := core_reset
  io.core_block_id := core_block_id
  io.core_thread_count := core_thread_count
  io.done := done

  when (reset.asBool) {
    done := false.B
    blocks_dispatched := 0.U
    blocks_done := 0.U
    start_execution := false.B
    for (i <- 0 until NUM_CORES) {
      core_start(i) := false.B
      core_reset(i) := true.B
      core_block_id(i) := 0.U
      core_thread_count(i) := THREADS_PER_BLOCK.U
    }
  } .elsewhen (io.start) {
    // EDA: Indirect way to get @(posedge start)
    when (!start_execution) {
      start_execution := true.B
      for (i <- 0 until NUM_CORES) {
        core_reset(i) := true.B
      }
    }

    // If the last block has finished processing, mark this kernel as done executing
    when (blocks_done === total_blocks) {
      done := true.B
    }

    // 分配新block
    for (i <- 0 until NUM_CORES) {
      when (core_reset(i)) {
        core_reset(i) := false.B
        when (blocks_dispatched < total_blocks) {
          core_start(i) := true.B
          core_block_id(i) := blocks_dispatched
          core_thread_count(i) := Mux(
            blocks_dispatched === (total_blocks - 1.U),
            io.thread_count - (blocks_dispatched * THREADS_PER_BLOCK.U),
            THREADS_PER_BLOCK.U
          )
          blocks_dispatched := blocks_dispatched + 1.U
        }
      }
    }

    // 处理完成的block
    for (i <- 0 until NUM_CORES) {
      when (core_start(i) && io.core_done(i)) {
        core_reset(i) := true.B
        core_start(i) := false.B
        blocks_done := blocks_done + 1.U
      }
    }
  }
}