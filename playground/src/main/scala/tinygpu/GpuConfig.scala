package tinygpu
import org.chipsalliance.cde.config._
// import freechips.rocketchip.config._
// import freechips.rocketchip.system.BaseConfig

// class WithMyGpu extends Config((site, here, up) => {
//   case BuildRoCC => (p: Parameters) => Seq(
//     LazyModule(new tinygpu.GpuLazyModule()(p))
//   )
// })
// Define all the keys for your GPU parameters
object GpuKeys {
  case object DataMemAddrBits extends Field[Int]
  case object DataMemDataBits extends Field[Int]
  case object DataMemNumChannels extends Field[Int]
  case object ProgramMemAddrBits extends Field[Int]
  case object ProgramMemDataBits extends Field[Int]
  case object ProgramMemNumChannels extends Field[Int]
  case object NumCores extends Field[Int]
  case object ThreadsPerBlock extends Field[Int]
}

import GpuKeys._
// This config defines the exact parameters you specified
class MySpecificGpuConfig extends Config((site, here, up) => {
  case DataMemAddrBits    => 8
  case DataMemDataBits    => 8
  case DataMemNumChannels => 4
  case ProgramMemAddrBits => 8
  case ProgramMemDataBits => 16
  case ProgramMemNumChannels => 1
  case NumCores           => 2
  case ThreadsPerBlock    => 4
})

class GpuConfig extends Config((site, here, up) => PartialFunction.empty)

// RoCC Config
// class GpuRoccConfig extends Config(
//   new WithMyGpu ++
//   new BaseConfig
// )