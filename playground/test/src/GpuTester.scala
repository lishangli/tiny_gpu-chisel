package tinygpu

// import tinygpu.configs.{MySpecificGpuConfig, GpuKeys} // 假设你的配置在这里
import chisel3._
import chiseltest._ // 使用 chiseltest 的 FlatSpec 和 tester
import org.chipsalliance.cde.config._
import org.scalatest.flatspec.AnyFlatSpec // 使用 AnyFlatSpec
import freechips.rocketchip.diplomacy.LazyModule
import scala.util.control.Breaks._

class GpuTester extends AnyFlatSpec with ChiselScalatestTester {
  "Gpu" should "run a simple test" in {
    // 1. 创建隐式 Parameters 对象
    implicit val p: Parameters = (new MySpecificGpuConfig).toInstance

    // 2. 实例化 LazyModule。如果 GpuLazyModule 定义为 (implicit p: Parameters)，则不需要 (p)。
    val gpuLazyModule = LazyModule(new GpuLazyModule)

    println(gpuLazyModule.module.thread_count)

    // 3. 将 LazyModule 的实际硬件模块传递给 test 函数
    test(gpuLazyModule.module) { dut =>
       withClockAndReset(dut.clock, dut.reset) {
      // 初始化所有输入端口到安全状态
      dut.io.device_control_write_enable.poke(false.B)
      dut.io.device_control_data.poke(0.U)
      dut.io.start.poke(false.B)
      // 其他输入端口也应该初始化
      // 例如，如果 program_mem_read_ready 是输入，也需要 poke
      dut.io.program_mem_read_ready.foreach(_.poke(true.B)) // 假设总是准备好读取
      dut.io.data_mem_read_ready.foreach(_.poke(true.B))   // 假设总是准备好读取
      dut.io.data_mem_write_ready.foreach(_.poke(true.B))  // 假设总是准备好写入

      // 等待几个周期让模块稳定
      dut.clock.step(5)

      // 启动 GPU
      dut.io.start.poke(true.B)
      dut.clock.step(1) // 启动信号通常只需要一个周期
      dut.io.start.poke(false.B) // 然后立即拉低

      // 循环仿真，直到 done 信号变为 true
      var finished = false
      for (_ <- 0 until 100) {
        // 检查 done 信号
        if (dut.io.done.peek().litToBoolean) {
          println("GPU done!")
          finished = true // 标记为完成
          // 在这里可以添加其他断言，例如检查最终结果等
          // dut.io.some_result_output.expect(expected_value.U)
          break
        }
        dut.clock.step(1)
      }

      // 如果循环结束但 GPU 仍未完成，则测试失败
      assert(finished, "GPU did not finish in 100 cycles")
    }}
  }
}