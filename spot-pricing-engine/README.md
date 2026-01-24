# Spot Pricing Engine

The **Spot Pricing Engine** is a high-performance Java module within
the [FX Trading Platform](https://github.com/nitindandriyal/fx-trading-platform) designed for real-time processing and
distribution of FX spot market data quotes. It leverages Simple Binary Encoding (SBE) for efficient message
serialization and Aeron for low-latency, high-throughput messaging over IPC. The engine processes raw quotes, applies
tiered pricing transformations (GOLD, SILVER, BRONZE), and publishes them to clients with features like liquidity
throttling, quote caching, and garbage-collection-free (GC-free) operation to minimize latency spikes.

## Features

- **Low-Latency Encoding/Decoding**: Uses SBE for ~100-200 ns quote encoding/decoding, faster than manual
  serialization (~500 ns).
- **Tiered Pricing**: Applies client-specific transformations (spread, markup, skew, signal) for GOLD, SILVER, and
  BRONZE tiers on Aeron streams `1001`, `1002`, and `1003`.
- **GC-Free Operation**: Pre-allocated arrays (`MAX_LEVELS=10`) in `PooledQuote` eliminate allocations during
  encoding/decoding, reducing GC pauses.
- **Liquidity Throttling**: Limits quote updates to 50 quotes/second per tier per instrument to manage bandwidth and
  prevent buffer overflow.
- **Quote Caching**: Stores up to 1000 quotes in `ConcurrentQuoteCache` to handle backpressure and ensure data
  availability.
- **Object Pooling**: Reuses `PooledQuote` objects via `ObjectPool` (default size: 10,000) to minimize allocations.
- **Aeron Integration**: Publishes quotes over `aeron:ipc` with 32 MB term buffers for high-throughput, low-latency
  messaging.
- **Extensibility**: SBE schema supports adding fields (e.g., `clientId`) or increasing price levels without breaking
  compatibility.
- **Monitoring**: Tracks buffer usage (`BufferTracker`), throttling events, cache contention, and pool exhaustion for
  performance tuning.

## Prerequisites

- **Java**: JDK 11 or higher (tested with OpenJDK 17).
- **Maven**: 3.8.0 or higher for dependency management.
- **Aeron**: Media Driver for IPC messaging (included via `aeron-all` dependency).
- **SBE Tool**: For generating encoder/decoder classes from `QuoteSchema.xml`.
- **Operating System**: Linux (recommended for `/dev/shm/aeron`) or macOS/Windows with adjusted Aeron directory.

## Setup

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/nitindandriyal/fx-trading-platform.git
   cd fx-trading-platform/spot-pricing-engine
   ```

2. **Install Dependencies**:
   Add the following to your `pom.xml`:
   ```xml
   <dependencies>
       <dependency>
           <groupId>io.aeron</groupId>
           <artifactId>aeron-all</artifactId>
           <version>1.50.0</version>
       </dependency>
       <dependency>
           <groupId>org.agrona</groupId>
           <artifactId>agrona</artifactId>
           <version>2.4.0</version>
       </dependency>
   </dependencies>
   ```

3. **Generate SBE Classes**:
    - Place `QuoteSchema.xml` in the `src/main/resources` directory.
    - Run the SBE tool to generate encoder/decoder classes:
      ```bash
      java -jar sbe-all-1.28.0.jar src/main/resources/QuoteSchema.xml
      ```
    - Move generated classes (`com.example.pricer.*`) to `src/main/java/com/example/pricer`.

4. **Configure Aeron**:
    - Set the Aeron directory to `/dev/shm/aeron` (Linux) or a local directory:
      ```java
      Aeron.Context context = new Aeron.Context().aeronDirName("/dev/shm/aeron");
      ```
    - Run the Aeron Media Driver:
      ```bash
      java -Daeron.counters.enabled=true -cp aeron-all-1.42.0.jar io.aeron.driver.MediaDriver
      ```

5. **Build the Project**:
   ```bash
   mvn clean install
   ```

## Usage

1. **Run the Pricing Engine**:
    - Execute the `Main` class to start the `Pricer`:
      ```bash
      java -cp target/spot-pricing-engine.jar com.example.pricer.Main
      ```
    - The engine subscribes to raw quotes on stream `1000` (`aeron:ipc`), processes them, and publishes transformed
      quotes to streams `1001` (GOLD), `1002` (SILVER), and `1003` (BRONZE).

2. **Publish a Raw Quote**:
    - Use the `Pricer#publishRawQuote` method to send a quote for processing:
      ```java
      double[] bids = {1.10, 1.099, 1.098};
      double[] asks = {1.11, 1.111, 1.112};
      double[] volumes = {1000.0, 2000.0, 3000.0};
      long valueDate = System.currentTimeMillis() + 86400000; // Tomorrow
      pricer.publishRawQuote("EUR/USD", bids, asks, volumes, valueDate);
      ```

3. **Monitor Output**:
    - Logs display publishing status, throttling events, and pool exhaustion:
      ```
      Published GOLD quote: EUR/USD
      Throttled SILVER quote: EUR/USD
      Object pool exhausted for EUR/USD
      Final buffer counts:
      Log buffer files in /dev/shm/aeron: 4
      CnC log buffers: 1
      Throttle Count: 789
      Cache Contention Count: 45
      Cache Size: 6
      Pool Failed Borrows: 23
      ```

4. **Tune Parameters**:
    - Adjust `ObjectPool` size (`poolSize=10000`) for higher throughput.
    - Modify `LiquidityThrottler` rate (`ratePerSecond=50`, `burstCapacity=100`) for stricter/lenient throttling.
    - Increase `MAX_LEVELS=20` in `PooledQuote` for deeper order books.

## Testing

1. **Unit Tests**:
    - Run unit tests to verify quote encoding/decoding and transformations:
      ```bash
      mvn test
      ```
    - Tests cover `PooledQuote`, `ClientTier`, and `ConcurrentQuoteCache`.

2. **Integration Tests**:
    - Simulate high quote volumes with multiple threads (see `Main` class).
    - Test backpressure by reducing `term-buffer-length`:
      ```java
      String CHANNEL = "aeron:ipc?term-buffer-length=65536";
      ```
    - Verify caching and throttling under load.

3. **Performance Tests**:
    - Measure encoding/decoding latency (~100-200 ns) with JMH benchmarks.
    - Monitor GC with `-XX:+PrintGCDetails` to confirm GC-free operation.
    - Check pool exhaustion (`failedBorrows`) and throttling (`throttleCount`).

4. **Extensibility**:
    - Add a `clientId` field to `QuoteSchema.xml`:
      ```xml
      <sbe:field name="clientId" id="9" type="varData"/>
      ```
    - Regenerate SBE classes and verify backward compatibility.

## Performance Considerations

- **Encoding/Decoding**: SBE encoding (~100-200 ns) is 2-5x faster than manual serialization, reducing **first quote
  slow path** latency.
- **Memory Usage**: Quote size ~108 bytes (3 levels). `poolSize=10,000` * ~160 bytes (object + arrays) = ~1.6 MB.
- **GC-Free**: Pre-allocated arrays (`MAX_LEVELS=10`) eliminate allocations in `PooledQuote#decode`. Minor allocations
  in `getBids`/`getAsks`/`getVolumes` are negligible.
- **Throughput**: Supports ~10,000 quotes/second with `poolSize=10,000` and throttling at 50 quotes/second/tier.
- **Contention**: SBE’s type safety reduces invalid messages, minimizing Aeron buffer contention. `ConcurrentQuoteCache`
  handles cache contention (monitored via `contentionCount`).
- **Backpressure**: Caching ensures no quote loss during Aeron backpressure (monitored via `result < 0` in
  `Publication#offer`).

## First Quote Slow Path Mitigation

The **first quote slow path** refers to initial latency spikes when processing the first quote due to JIT compilation,
buffer allocation, or contention. The engine mitigates this through:

- **Fast SBE Encoding**: ~100-200 ns reduces processing overhead.
- **GC-Free Design**: Pre-allocated arrays prevent GC pauses.
- **Type Safety**: SBE validators reduce invalid messages, lowering Aeron errors.
- **Throttling**: Limits quote bursts to manage buffer usage (50 quotes/second/tier).
- **Caching**: Stores quotes during backpressure, ensuring availability.
- **Metrics**: `failedBorrows`, `throttleCount`, `contentionCount`, and `BufferTracker` aid tuning.

## License

This project is licensed under the MIT License. See
the [LICENSE](https://github.com/nitindandriyal/fx-trading-platform/blob/main/LICENSE) file for details.

## Acknowledgments

- [Aeron](https://github.com/real-logic/aeron) for high-performance messaging.
- [Simple Binary Encoding](https://github.com/real-logic/simple-binary-encoding) for efficient serialization.
