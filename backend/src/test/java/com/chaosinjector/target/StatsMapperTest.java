package com.chaosinjector.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.chaosinjector.target.model.Stats;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Statistics;

/**
 * Unit-tests the docker-stats arithmetic without a daemon by deserialising a
 * representative stats frame (the same JSON shape the Docker Engine streams).
 */
class StatsMapperTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // cpu delta = 200000000-100000000 = 1e8; system delta = 2000000000-1000000000 = 1e9;
    // onlineCpus = 4 -> percent = (1e8/1e9)*4*100 = 40.0
    private static final String JSON = """
        {
          "cpu_stats": {
            "cpu_usage": { "total_usage": 200000000, "percpu_usage": [1,2,3,4] },
            "system_cpu_usage": 2000000000,
            "online_cpus": 4
          },
          "precpu_stats": {
            "cpu_usage": { "total_usage": 100000000 },
            "system_cpu_usage": 1000000000
          },
          "memory_stats": { "usage": 524288000, "limit": 1048576000 },
          "networks": {
            "eth0": { "rx_bytes": 1000, "tx_bytes": 2000 },
            "eth1": { "rx_bytes": 500, "tx_bytes": 250 }
          },
          "blkio_stats": {
            "io_service_bytes_recursive": [
              { "op": "Read", "value": 4096 },
              { "op": "Write", "value": 8192 }
            ]
          },
          "pids_stats": { "current": 7 }
        }
        """;

    @Test
    void mapsAllFieldsAndComputesCpuLikeDockerCli() throws Exception {
        Statistics stats = mapper.readValue(JSON, Statistics.class);
        Stats s = StatsMapper.toStats(stats);

        assertThat(s.cpuPercent()).isCloseTo(40.0, within(0.001));
        assertThat(s.memoryUsageBytes()).isEqualTo(524288000L);
        assertThat(s.memoryLimitBytes()).isEqualTo(1048576000L);
        assertThat(s.memoryPercent()).isCloseTo(50.0, within(0.001));
        assertThat(s.netRxBytes()).isEqualTo(1500L);
        assertThat(s.netTxBytes()).isEqualTo(2250L);
        assertThat(s.blkReadBytes()).isEqualTo(4096L);
        assertThat(s.blkWriteBytes()).isEqualTo(8192L);
        assertThat(s.pids()).isEqualTo(7L);
    }

    @Test
    void zeroSystemDeltaYieldsZeroCpu() throws Exception {
        String json = """
            {
              "cpu_stats": { "cpu_usage": { "total_usage": 100 }, "system_cpu_usage": 1000, "online_cpus": 2 },
              "precpu_stats": { "cpu_usage": { "total_usage": 100 }, "system_cpu_usage": 1000 },
              "memory_stats": { "usage": 0, "limit": 0 }
            }
            """;
        Statistics stats = mapper.readValue(json, Statistics.class);
        Stats s = StatsMapper.toStats(stats);
        assertThat(s.cpuPercent()).isEqualTo(0.0);
        assertThat(s.memoryPercent()).isEqualTo(0.0);
    }
}
