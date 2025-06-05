# Tiny GPU(chisel version)

## Introduction
This is a project refering to [tiny-gpu](https://github.com/adam-maj/tiny-gpu). 

## Quick Start

To use this repo as your Chisel development environment, simply follow the steps.

0. Clone this repo;

```bash
git clone git@github.com:lishangli/tiny_gpu-chisel.git
```

0. Install dependencies and setup environments:
- Arch Linux `pacman -Syu --noconfirm make parallel wget cmake ninja mill dtc verilator git llvm clang lld protobuf antlr4 numactl`
- Nix ` nix develop --extra-experimental-features nix-command --extra-experimental-features flakes`

0. [Optional] Remove unused dependences to accelerate bsp compile in `build.sc` `playground.moduleDeps`;

```bash
cd playground # entry your project directory
vim build.sc
```

```scala
// build.sc

// Original
object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, inclusivecache, blocks, rocketdsputils, shells, firesim, boom, chipyard, chipyard.fpga, chipyard.utilities, mychiseltest)
  ...
}

// Remove unused dependences, e.g.,
object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(mychiseltest)
  ...
}
```


0. Init and update dependences;

```bash
cd playground # entry your project directory
make init     # init the submodules
make patch    # using the correct patches for some repos
```


0. Generate IDE bsp;

```bash
make bsp
```


0. Open your IDE and wait bsp compile;

```bash
idea . # open IDEA at current directory
```


# Project Structure

```
.
├── src
│   └── main
│       └── scala
│           ├── Convert.scala
│           ├── Main.scala
│           ├── Playground.scala
│           └── tinygpu
│               ├── ALU.scala
│               ├── Controller.scala
│               ├── Core.scala
│               ├── DCR.scala
│               ├── Decoder.scala
│               ├── Dispatch.scala
│               ├── Fetcher.scala
│               ├── Gpu.scala
│               ├── GpuConfig.scala
│               ├── LSU.scala
│               ├── PC.scala
│               ├── Registers.scala
│               ├── Scheduler.scala
│               └── Tester.scala
└── test
    └── src
        ├── GpuTester.scala
        └── tinygpu
```

## IDE support
For mill use
```bash
mill mill.bsp.BSP/install
```
then open by your favorite IDE, which supports [BSP](https://build-server-protocol.github.io/) 

## Pending PRs
Philosophy of this repository is **fast break and fast fix**.
This repository always tracks remote developing branches, it may need some patches to work, `make patch` will append below in sequence:
<!-- BEGIN-PATCH -->
rocket-chip-inclusive-cache https://github.com/chipsalliance/rocket-chip-inclusive-cache/pull/22.diff
<!-- END-PATCH -->

## Always keep update-to-date
You can use this template and start your own job by appending commits on it. GitHub Action will automatically bump all dependencies, you can merge or rebase `chipsalliance/master` to your branch.

```bash
cd playground # entry your project directory
git rebase origin/master
```

# ToDO
- [ ] Add tests
- [ ] Fix issues in designs
