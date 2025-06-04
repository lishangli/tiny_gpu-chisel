// 文件名: Controller.scala
package tinygpu

import chisel3._
import chisel3.util._

class Controller(
  val ADDR_BITS: Int = 8,
  val DATA_BITS: Int = 16,
  val NUM_CONSUMERS: Int = 4,
  val NUM_CHANNELS: Int = 1,
  val WRITE_ENABLE: Boolean = true
) extends Module {
  val io = IO(new Bundle {
    // Consumer Interface
    val consumer_read_valid    = Input(Vec(NUM_CONSUMERS, Bool()))
    val consumer_read_address  = Input(Vec(NUM_CONSUMERS, UInt(ADDR_BITS.W)))
    val consumer_read_ready    = Output(Vec(NUM_CONSUMERS, Bool()))
    val consumer_read_data     = Output(Vec(NUM_CONSUMERS, UInt(DATA_BITS.W)))
    val consumer_write_valid   = Input(Vec(NUM_CONSUMERS, Bool()))
    val consumer_write_address = Input(Vec(NUM_CONSUMERS, UInt(ADDR_BITS.W)))
    val consumer_write_data    = Input(Vec(NUM_CONSUMERS, UInt(DATA_BITS.W)))
    val consumer_write_ready   = Output(Vec(NUM_CONSUMERS, Bool()))

    // Memory Interface
    val mem_read_valid    = Output(Vec(NUM_CHANNELS, Bool()))
    val mem_read_address  = Output(Vec(NUM_CHANNELS, UInt(ADDR_BITS.W)))
    val mem_read_ready    = Input(Vec(NUM_CHANNELS, Bool()))
    val mem_read_data     = Input(Vec(NUM_CHANNELS, UInt(DATA_BITS.W)))
    val mem_write_valid   = Output(Vec(NUM_CHANNELS, Bool()))
    val mem_write_address = Output(Vec(NUM_CHANNELS, UInt(ADDR_BITS.W)))
    val mem_write_data    = Output(Vec(NUM_CHANNELS, UInt(DATA_BITS.W)))
    val mem_write_ready   = Input(Vec(NUM_CHANNELS, Bool()))
  })

  // 状态定义
  val IDLE          = 0.U(3.W)
  val READ_WAITING  = 2.U(3.W)
  val WRITE_WAITING = 3.U(3.W)
  val READ_RELAYING = 4.U(3.W)
  val WRITE_RELAYING= 5.U(3.W)

  // 通道状态
  val controller_state = RegInit(VecInit(Seq.fill(NUM_CHANNELS)(IDLE)))
  val current_consumer = RegInit(VecInit(Seq.fill(NUM_CHANNELS)(0.U(log2Ceil(NUM_CONSUMERS).W))))
  val channel_serving_consumer = RegInit(VecInit(Seq.fill(NUM_CONSUMERS)(false.B)))

  // 输出寄存器
  io.mem_read_valid    := VecInit(Seq.fill(NUM_CHANNELS)(false.B))
  io.mem_read_address  := VecInit(Seq.fill(NUM_CHANNELS)(0.U))
  io.mem_write_valid   := VecInit(Seq.fill(NUM_CHANNELS)(false.B))
  io.mem_write_address := VecInit(Seq.fill(NUM_CHANNELS)(0.U))
  io.mem_write_data    := VecInit(Seq.fill(NUM_CHANNELS)(0.U))
  io.consumer_read_ready  := VecInit(Seq.fill(NUM_CONSUMERS)(false.B))
  io.consumer_read_data   := VecInit(Seq.fill(NUM_CONSUMERS)(0.U))
  io.consumer_write_ready := VecInit(Seq.fill(NUM_CONSUMERS)(false.B))

  // 状态机
  for (i <- 0 until NUM_CHANNELS) {
    switch(controller_state(i)) {
      is (IDLE) {
        // 轮询所有消费者
        var found = false.B
        for (j <- 0 until NUM_CONSUMERS) {
          when(!found && io.consumer_read_valid(j) && !channel_serving_consumer(j)) {
            channel_serving_consumer(j) := true.B
            current_consumer(i) := j.U
            io.mem_read_valid(i) := true.B
            io.mem_read_address(i) := io.consumer_read_address(j)
            controller_state(i) := READ_WAITING
            found = true.B
          } .elsewhen(!found && io.consumer_write_valid(j) && !channel_serving_consumer(j)) {
            channel_serving_consumer(j) := true.B
            current_consumer(i) := j.U
            io.mem_write_valid(i) := true.B
            io.mem_write_address(i) := io.consumer_write_address(j)
            io.mem_write_data(i) := io.consumer_write_data(j)
            controller_state(i) := WRITE_WAITING
            found = true.B
          }
        }
      }
      is (READ_WAITING) {
        when(io.mem_read_ready(i)) {
          io.mem_read_valid(i) := false.B
          io.consumer_read_ready(current_consumer(i)) := true.B
          io.consumer_read_data(current_consumer(i)) := io.mem_read_data(i)
          controller_state(i) := READ_RELAYING
        }
      }
      is (WRITE_WAITING) {
        when(io.mem_write_ready(i)) {
          io.mem_write_valid(i) := false.B
          io.consumer_write_ready(current_consumer(i)) := true.B
          controller_state(i) := WRITE_RELAYING
        }
      }
      is (READ_RELAYING) {
        when(!io.consumer_read_valid(current_consumer(i))) {
          channel_serving_consumer(current_consumer(i)) := false.B
          io.consumer_read_ready(current_consumer(i)) := false.B
          controller_state(i) := IDLE
        }
      }
      is (WRITE_RELAYING) {
        when(!io.consumer_write_valid(current_consumer(i))) {
          channel_serving_consumer(current_consumer(i)) := false.B
          io.consumer_write_ready(current_consumer(i)) := false.B
          controller_state(i) := IDLE
        }
      }
    }
  }

  // 异步复位
  when (reset.asBool) {
    for (i <- 0 until NUM_CHANNELS) {
      controller_state(i) := IDLE
      current_consumer(i) := 0.U
    }
    for (j <- 0 until NUM_CONSUMERS) {
      channel_serving_consumer(j) := false.B
    }
  }
}