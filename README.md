# Simulasi Penjadwalan Cloud Task Menggunakan Algoritma Heuristik Sufferage

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-blue.svg)](https://maven.apache.org/)
[![CloudSim Plus](https://img.shields.io/badge/CloudSim%20Plus-8.5.4-green.svg)](https://cloudsimplus.org/)
[![License](https://img.shields.io/badge/License-Academic%20Use%20Only-lightgrey.svg)](#)

Implementasi simulasi optimasi task scheduling pada infrastruktur komputasi awan heterogen (*multi-datacenter*) menggunakan **Algoritma Heuristik Sufferage** berbasis **CloudSim Plus**.

---

## 👥 Anggota Kelompok 7

| No | Nama Anggota | NRP |
|:--:|:---|:---|
| 1 | Imam Mahmud Dalil Fauzan | 5027241100 |
| 2 | Ahmad Yazid Arifuddin | 5027241040 |
| 3 | Christiano Ronaldo Silalahi | 5027241025 |
| 4 | Oscaryavat Viryavan | 5027241053 |
| 5 | Adiwidya Budi Pratama | 5027241012 |

---

## 📌 Ringkasan Proyek

Task scheduling merupakan salah satu tantangan pada lingkungan cloud computing. Kompleksitas bertambah ketika infrastruktur terdistribusi dan beragam, di mana setiap node komputasi punya kapasitas CPU (MIPS), core, memori, dan bandwidth yang beda-beda.

Algoritma konvensional seperti Round Robin atau First-Come First-Served rentan menyebabkan:
1. **Makespan yang tinggi**: Waktu penyelesaian keseluruhan menjadi lama karena tugas besar dialokasikan ke kemampuan mesin dengan kapasitas rendah.
2. **Ketidakseimbangan beban (Imbalance)**: Sebagian mesin mengalami *overload*, sedangkan mesin lain menganggur (*idle*).

Di proyek ini, diimplementasikan algoritma **Sufferage Heuristic** (yang menjadi pondasi pendekatan *Load Balanced Min-Min / LBMM*). Algoritma ini memprioritaskan tasks berdasarkan nilai *sufferage* (selisih waktu penyelesaian terbaik pertama dan kedua) untuk meminimalkan *Makespan* sekaligus menjaga resource.

---

## 🎯 Objektif & Metrik Evaluasi

Simulasi ini mengukur efektivitas penjadwalan berdasarkan 5 metrik utama:

### 1. Makespan
Total waktu yang dibutuhkan untuk menyelesaikan seluruh cloudlet dalam simulasi.
Semakin kecil nilai makespan, semakin baik kinerjanya.
$$\text{Makespan} = \max_{j \in \text{Cloudlets}} (FT_j)$$
*Di mana $FT_j$ adalah waktu selesai (finish time) dari cloudlet $j$.*

### 2. Degree of Imbalance (DI)
Tingkat ketidakseimbangan load kerja antar VM. Semakin kecil nilai DI, semakin merata distribusi beban kerja antar VM.
$$DI = \frac{T_{max} - T_{min}}{T_{avg}}$$
*Di mana $T_{max}$, $T_{min}$, dan $T_{avg}$ berturut-turut adalah beban waktu eksekusi VM maksimum, minimum, dan rata-rata.*

### 3. Resource Utilization (RU)
Persentase rata-rata pemanfaatan kemampuan komputasi VM.
Semakin tinggi nilai RU, semakin baik pemanfaatan sumber daya.
$$RU = \frac{\sum_{i=1}^{m} \text{Beban}_i}{m \times \text{Makespan}} \times 100\%$$
*Di mana $m$ adalah jumlah VM aktif.*

### 4. Throughput
Jumlah tugas yang berhasil diselesaikan per detik.
Semakin tinggi nilai throughput, semakin baik kinerja algoritma penjadwalan.
$$\text{Throughput} = \frac{N}{\text{Makespan}}$$
*Di mana $N$ adalah total cloudlet yang diselesaikan.*

### 5. Average Response Time
Rata-rata waktu yang dibutuhkan untuk menyelesaikan tugas sejak submission hingga selesai.
Semakin kecil nilai ART, semakin baik kinerja algoritma penjadwalan.
$$\text{ART} = \frac{1}{N} \sum_{j=1}^{N} (FT_j - ST_j)$$

---

## 🏗️ Arsitektur Simulasi

Skenario simulasi merepresentasikan lingkungan *multi-cloud datacenter*:

```mermaid
graph TD
    subgraph Client_Broker["Client/Broker"]
        Broker["DatacenterBrokerSimple"]
        Scheduler["Sufferage Scheduler"]
        Workload["GoCJ Workload: 500 Tasks"]
    end

    subgraph DC1["Datacenter 1 - DC1"]
        H1["Host 0 - Tipe A: 2 Core"]
        H2["Host 1 - Tipe B: 4 Core"]
        H3["Host 2 - Tipe C: 8 Core"]
        H4["Host 3 - Tipe A: 2 Core"]
        H5["Host 4 - Tipe B: 4 Core"]
    end

    subgraph DC2["Datacenter 2 - DC2"]
        H6["Host 5 - Tipe A: 2 Core"]
        H7["Host 6 - Tipe B: 4 Core"]
        H8["Host 7 - Tipe C: 8 Core"]
        H9["Host 8 - Tipe A: 2 Core"]
        H10["Host 9 - Tipe B: 4 Core"]
    end

    Workload --> Scheduler
    Scheduler --> Broker
    Broker -->|Alokasi VM & Cloudlet| DC1
    Broker -->|Alokasi VM & Cloudlet| DC2

```

### Spesifikasi Infrastruktur

#### 1. Host Fisik (10 Host, 5 Host/Datacenter)
| Tipe Host | Core (PE) | MIPS / Core | Total MIPS | RAM (MB) | Bandwidth | Storage | Kategori |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| **Tipe A** | 2 | 2.000 | 4.000 | 4.096 (4 GB) | 10.000 Mbps | 1.000.000 MB | Rendah |
| **Tipe B** | 4 | 3.000 | 12.000 | 8.192 (8 GB) | 10.000 Mbps | 1.000.000 MB | Sedang |
| **Tipe C** | 8 | 4.000 | 32.000 | 16.384 (16 GB) | 10.000 Mbps | 1.000.000 MB | Tinggi |

#### 2. Virtual Machine (20 VM, 10 VM/Datacenter)
| Profil VM | vCPU (PE) | MIPS | RAM (MB) | Bandwidth | Kebijakan Penjadwalan |
|:---|:---:|:---:|:---:|:---:|:---|
| **VM Kecil** | 1 | 1.800 | 1.024 (1 GB) | 1.000 Mbps | Time-Shared Scheduler |
| **VM Sedang** | 2 | 2.800 | 2.048 (2 GB) | 1.000 Mbps | Time-Shared Scheduler |
| **VM Besar** | 4 | 3.800 | 4.096 (4 GB) | 1.000 Mbps | Time-Shared Scheduler |

#### 3. Dataset Beban Kerja (Google Cloud Jobs - GoCJ)
Simulasi menggunakan 500 cloudlet yang merepresentasikan variasi karakteristik komputasi nyata berdasarkan distribusi dataset **GoCJ (Google Cloud Jobs)**:
- **Small Task**: $1.000 - 10.000$ Million Instructions (MI)
- **Medium Task**: $10.000 - 50.000$ MI
- **Large Task**: $50.000 - 100.000$ MI
- **Extra Large Task**: $100.000 - 200.000$ MI
- **Huge Task**: $200.000 - 300.000$ MI

---

## 🧠 Cara Kerja Algoritma Sufferage

Algoritma Sufferage merupakan algoritma heuristik berbasis *completion time* yang didesain untuk mencegah penundaan tugas yang memiliki ketergantungan tinggi pada mesin cepat.

```mermaid
flowchart TD
    Start([Mulai]) --> Init[Inisialisasi ReadyTime seluruh VM = 0]
    Init --> CheckQueue{Apakah masih ada task belum terjadwal?}
    CheckQueue -- Tidak --> Done([Selesai])
    CheckQueue -- Ya --> LoopTasks[Untuk setiap task tak terjadwal:]
    LoopTasks --> CalcCT[Hitung Completion Time di setiap VM:<br/>CT = ReadyTime + TaskLength / Vm_MIPS]
    CalcCT --> FindBests[Cari CT tercepat pertama CT_1<br/>dan tercepat kedua CT_2]
    FindBests --> CalcSuff[Hitung Sufferage Value = CT_2 - CT_1]
    CalcSuff --> PickMax[Pilih task dengan Sufferage Value TERBESAR]
    PickMax --> Assign[Petakan task terpilih ke VM terbaiknya]
    Assign --> UpdateRT[Perbarui ReadyTime VM = CT_1]
    UpdateRT --> RemoveTask[Hapus task dari antrean belum terjadwal]
    RemoveTask --> CheckQueue
```

---

## 📂 Struktur Direktori Proyek

```text
.
├── pom.xml                               # Konfigurasi dependensi Maven & Shade plugin
├── README.md                             # Dokumentasi proyek
├── src
│   └── main
│       ├── java
│       │   └── soka
│       │       ├── Kelompok7Simulation.java   # Main Class orkestrasi simulasi CloudSim Plus
│       │       ├── GoCJLoader.java           # Loader & Synthetic Generator dataset GoCJ
│       │       ├── MetricsCalculator.java    # Kalkulator metrik performa (Makespan, DI, RU)
│       │       └── scheduler
│       │           ├── SufferageScheduler.java    # Implementasi Algoritma Heuristik Sufferage
│       │           └── RoundRobinScheduler.java   # Baseline Scheduler pembanding
│       └── resources
│           └── GoCJ_Dataset_500.txt          # (Opsional) File dataset riil GoCJ
└── target                                    # Direktori hasil kompilasi binary & JAR
```

---

## 🚀 Cara Menjalankan Simulasi

- **Java Development Kit (JDK)**: Versi 17 atau lebih baru (`java -version`).
- **Apache Maven**: Versi 3.8+ (`mvn -version`) atau gunakan Maven Wrapper (`mvnw`).

### Run via Terminal / CLI

1. **Compile**
   ```bash
   mvn clean package
   ```

2. **Jalankan Simulasi:**
   ```bash
   java -jar target/soka-simulasi.jar
   ```
---

## 📊 Hasil Output Simulasi

```text
================== Starting CloudSim Plus 8.5.1 ==================
Datacenter dibuat: DC1 & DC2 (masing-masing 5 Host, total 10 Host heterogen)
Total VM dibuat: 20
Total Cloudlet (task) dari GoCJ: 500

>>> Menjalankan simulasi dengan algoritma Sufferage (LBMM)...
...
================== Simulation finished at time 1822.46 ==================

Cloudlet selesai: 500 dari 500

================ HASIL: Sufferage Heuristic (LBMM) ================
Makespan               : 1822.35 detik
Degree of Imbalance(DI): 1.3400
Resource Utilization   : 56.68%
Throughput             : 0.2744 task/detik
Avg. Response Time     : 399.43 detik
Jumlah VM aktif dipakai: 20
=================================================
```

---

## 🔮 Rencana Pengembangan Selanjutnya (Roadmap)

- [v] **Implementasi Baseline Scheduler**: *Round Robin* ([RoundRobinScheduler.java](file:///src/main/java/soka/scheduler/RoundRobinScheduler.java)).
- [v] **Implementasi Heuristic Scheduler**: *Sufferage (LBMM Foundation)* ([SufferageScheduler.java](file:///src/main/java/soka/scheduler/SufferageScheduler.java)).
- [ ] **Implementasi Metaheuristik Bio-Inspired**:
  - **Cat Swarm Optimization (CSO)**
  - **Coati Optimization Algorithm (COA)**
- [ ] **Implementasi Algoritma Hybrid**:
  - **Hybrid PSO + CSO** untuk optimasi multi-objektif (Makespan, Energi, dan Cost).
- [ ] **Visualisasi & Analisis Komparatif**: Ekspor log ke format CSV/Excel dan pembuatan grafik perbandingan performa antar algoritma.

---

## 📚 Referensi Akademik

1. **CloudSim Plus Framework**:  
   Silva Filho, M. C., Oliveira, R. L., Monteiro, C. C., Inácio, P. R., & Freire, M. M. (2017). *CloudSim Plus: a modern Java 8 framework for modeling and simulation of cloud computing infrastructures*. Software: Practice and Experience, 47(9), 1309-1345.
2. **GoCJ Dataset**:  
   Ghafari, R., & Mansouri, N. (2020). *GoCJ: A Google cloud jobs dataset for cloud computing simulation*. Mendeley Data, V1.
3. **Sufferage Scheduling in Heterogeneous Environments**:  
   Maheswaran, M., Ali, S., Siegel, H. J., Hensgen, D., & Freund, R. F. (1999). *Dynamic matching and scheduling of a class of independent tasks onto heterogeneous computing systems*. Proceedings Eighth Heterogeneous Computing Workshop (HCW'99), 30-44.
4. **Load Balanced Min-Min (LBMM)**:  
   Kokilavani, T., & Amalarethinam, D. I. G. (2011). *Load balanced min-min algorithm for static meta-task scheduling in grid computing*. International Journal of Computer Applications, 20(2), 43-49.
