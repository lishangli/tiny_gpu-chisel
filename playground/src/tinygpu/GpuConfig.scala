package tinygpu
import org.chipsalliance.cde.config.Config

class WithMyGpu extends Config((site, here, up) => {
  case BuildRoCC => (p: Parameters) => Seq(
    LazyModule(new tinygpu.GpuLazyModule()(p))
  )
})

class GpuConfig extends Config((site, here, up) => PartialFunction.empty)

// RoCC Config
class GpuRoccConfig extends Config(
  new WithMyGpu ++
  new BaseConfig
)