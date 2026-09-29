// Sourced presets; see docs/TOOLS_AUDIT_2026-09-29.md.
const VTX_DB = [
  {
    "mfr": "SpeedyBee",
    "name": "TX800 \u2014 US",
    "proto": 8192,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable powerlevels 5\nvtxtable powervalues 25 200 400 600 600\nvtxtable powerlabels 25 200 400 800 800\nvtxtable band 1 BOSCAM_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BOSCAM_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BOSCAM_E E CUSTOM 5705 5685 5665 0 5885 5905 0 0\nvtxtable band 4 FATSHARK F CUSTOM 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R CUSTOM 5658 5695 5732 5769 5806 5843 5880 5917",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/speedybee_tx800.txt"
  },
  {
    "mfr": "SpeedyBee",
    "name": "TX800 \u2014 EU",
    "proto": 8192,
    "table": "vtxtable bands 4\nvtxtable channels 8\nvtxtable powerlevels 5\nvtxtable powervalues 25 200 400 600 600\nvtxtable powerlabels 25 200 400 800 800\nvtxtable band 1 BOSCAM_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 0\nvtxtable band 2 BOSCAM_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 FATSHARK F CUSTOM 5740 5760 5780 5800 5820 5840 5860 0\nvtxtable band 4 RACEBAND R CUSTOM 0 0 0 5769 5806 5843 0 0",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/speedybee_tx800.txt"
  },
  {
    "mfr": "Rush",
    "name": "Tank Solo (SA 2.1)",
    "proto": 2048,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BAND_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BAND_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BAND_E E CUSTOM 5705 5685 5665 5665 5885 5905 5905 5905\nvtxtable band 4 AIRWAVE F CUSTOM 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R CUSTOM 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 4\nvtxtable powervalues 14 26 29 32\nvtxtable powerlabels 25 400 800 MAX",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/rush_sa2.1_vtx_tables.txt"
  },
  {
    "mfr": "Rush",
    "name": "Tiny Tank (SA 2.1)",
    "proto": 2048,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BAND_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BAND_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BAND_E E CUSTOM 5705 5685 5665 5665 5885 5905 5905 5905\nvtxtable band 4 AIRWAVE F CUSTOM 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R CUSTOM 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 4\nvtxtable powervalues 14 20 23 25\nvtxtable powerlabels 25 100 200 350",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/rush_sa2.1_vtx_tables.txt"
  },
  {
    "mfr": "Rush",
    "name": "Tank Racing (SA 2.1)",
    "proto": 2048,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BAND_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BAND_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BAND_E E CUSTOM 5705 5685 5665 5665 5885 5905 5905 5905\nvtxtable band 4 AIRWAVE F CUSTOM 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R CUSTOM 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 4\nvtxtable powervalues 14 17 23 27\nvtxtable powerlabels 25 50 200 500",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/rush_sa2.1_vtx_tables.txt"
  },
  {
    "mfr": "Rush",
    "name": "Tank Ultimate Plus/Mini/II (SA 2.1)",
    "proto": 2048,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BAND_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BAND_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BAND_E E CUSTOM 5705 5685 5665 5665 5885 5905 5905 5905\nvtxtable band 4 AIRWAVE F CUSTOM 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R CUSTOM 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 4\nvtxtable powervalues 14 23 27 29\nvtxtable powerlabels 25 200 500 800",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/rush_sa2.1_vtx_tables.txt"
  },
  {
    "mfr": "Foxeer",
    "name": "Reaper Extreme 2.5W 40Ch",
    "proto": 8192,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BOSCAM_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BOSCAM_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BOSCAM_E E CUSTOM 5705 5685 5665 5645 5885 5905 5925 5945\nvtxtable band 4 FATSHARK F CUSTOM 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R CUSTOM 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 5\nvtxtable powervalues 25 100 200 400 600\nvtxtable powerlabels 25 200 500 1.5 2.5",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/Foxeer_Reaper_Extreme.txt"
  },
  {
    "mfr": "TBS",
    "name": "Unify Pro32 HV (SA 2.1)",
    "proto": 2048,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BOSCAM_A A FACTORY 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BOSCAM_B B FACTORY 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BOSCAM_E E FACTORY 5705 5685 5665 5645 5885 5905 5925 5945\nvtxtable band 4 FATSHARK F FACTORY 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R FACTORY 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 4\nvtxtable powervalues 14 20 26 36\nvtxtable powerlabels 25 100 400 1W+",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/Unify_Pro32_HV_SA2_1.txt"
  },
  {
    "mfr": "TBS",
    "name": "Unify Pro HV 800mW / Pro V3 (SA 2.0)",
    "proto": 2048,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BOSCAM_A A FACTORY 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BOSCAM_B B FACTORY 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BOSCAM_E E FACTORY 5705 5685 5665 5645 5885 5905 5925 5945\nvtxtable band 4 FATSHARK F FACTORY 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R FACTORY 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 4\nvtxtable powervalues 0 1 2 3\nvtxtable powerlabels 25 200 500 800",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/Unify_Pro_HV_800mw_Unify_Pro_V3_SA_2_0.txt"
  },
  {
    "mfr": "ImmersionRC",
    "name": "Tramp Nano / HV",
    "proto": 8192,
    "table": "vtxtable bands 5\nvtxtable channels 8\nvtxtable band 1 BOSCAM_A A CUSTOM 5865 5845 5825 5805 5785 5765 5745 5725\nvtxtable band 2 BOSCAM_B B CUSTOM 5733 5752 5771 5790 5809 5828 5847 5866\nvtxtable band 3 BOSCAM_E E CUSTOM 5705 5685 5665 5645 5885 5905 5925 5945\nvtxtable band 4 FATSHARK F CUSTOM 5740 5760 5780 5800 5820 5840 5860 5880\nvtxtable band 5 RACEBAND R CUSTOM 5658 5695 5732 5769 5806 5843 5880 5917\nvtxtable powerlevels 5\nvtxtable powervalues 25 100 200 400 600\nvtxtable powerlabels 25 100 200 400 600",
    "channel": 1,
    "source": "https://github.com/betaflight/firmware-presets/blob/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx/tramp_nano_and_HV.txt"
  }
];
