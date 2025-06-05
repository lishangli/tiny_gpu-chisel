package tinygpu

import chisel3._

class DCR extends Module {
  val io = IO(new Bundle {
    val device_control_write_enable = Input(Bool())
    val device_control_data        = Input(UInt(8.W))
    val thread_count               = Output(UInt(8.W))
  })

  val device_control_register = RegInit(0.U(8.W))

  when (reset.asBool) {
    device_control_register := 0.U
  } .elsewhen (io.device_control_write_enable) {
    device_control_register := io.device_control_data
  }

  io.thread_count := device_control_register
}