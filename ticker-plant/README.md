# Market Ticker Plant

A high-performance **ticker plant** serves as the central nervous system for real-time market data. It acts as a specialized "traffic cop" that ingests, normalizes, and distributes massive streams of financial data to downstream trading systems with ultra-low latency.

## 🚀 Core Functionality

The ticker plant sits at the entry point of a trading architecture, performing three critical roles:

1.  **Normalization**: Ingests raw data from multiple venues (NYSE, NASDAQ, LSE) and converts various exchange protocols into a single, unified format.
2.  **Distribution**: Manages subscriptions for "downstream" clients like algorithmic trading engines, real-time databases (RDBs), and visual dashboards.
3.  **Persistence (Journaling)**: Writes every incoming message to an append-only log file. This ensures data integrity and allows for rapid system recovery in the event of a crash.

## 🛠 Key Architecture Components

*   **Feed Handlers**: Interface directly with exchange gateways to pull raw data.
*   **The Ticker Plant (TP)**: The central hub that pushes data to subscribers and logs to the journal.
*   **Real-Time Database (RDB)**: Stores the current day's data in memory for instant querying.
*   **Historical Database (HDB)**: Stores previous days' data on disk for backtesting and analysis.

## ⚡ Performance Requirements

*   **Microsecond Latency**: Designed to process millions of messages per second to ensure traders have the most current "top of book" prices.
*   **Fault Tolerance**: High-availability configurations to prevent data gaps during peak market volatility.
*   **Scalability**: Ability to handle massive spikes in message rates during major economic events.
    
General Architecture Diagram:

                Exchange Feeds
            (ITCH / OUCH / FIX / FAST)
                    │
                    ▼
            +--------------------+
            |   Feed Handlers    |
            |  (Protocol Decode) |
            +--------------------+
                    │
                    ▼
              Normalized Events
                    │
                    ▼
            +------------------+
            |  Ingress Queue   |
            |  (Agrona RB)     |
            +------------------+
                    │
                    ▼
            +------------------+
            |  Ticker Plant    |
            |  Aggregator      |
            +------------------+
                │         │
                │         │
                ▼         ▼
            Journal     OrderBook
            (Replay)    Builder
                │          │
                └───┬──────┘
                    ▼
            Golden Tick Generator
                    │
                    ▼
            Aeron Multicast Bus
                    │
        ┌───────────┼─────────────┐
        ▼           ▼             ▼
    Algo Engine    Risk       RealTime DB
