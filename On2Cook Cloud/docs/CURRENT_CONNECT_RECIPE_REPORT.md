# Current Connect Recipe Payload Report

Date: 2026-06-12

This report covers the current 10 recipes that are sent to the device during connect sync.

## Result

- For all 10 current recipes, the BLE upload payload is the exact raw text from the ZIP archive in Downloads.
- No JSON reserialization is used for these connect-sync uploads when `rawRecipeText` is present.
- The current firmware does not expose a BLE recipe read-back command, so device-side stored bytes cannot yet be read back and compared byte-for-byte from the web app.

## Recipes

| Recipe | ZIP file | Text entry | Raw payload bytes | SHA256 (first 12) |
|---|---|---|---:|---|
| VEGETABLE UPMA | VEGETABLE UPMA.zip | VEGETABLE UPMA.txt | 2531 | `93d964a41edf` |
| VEG HAKKA NOODLE | VEG HAKKA NOODLE.zip | VEG HAKKA NOODLE.txt | 2701 | `7a7df3511a7d` |
| PAAL PAYASAM | PAAL PAYASAM .zip | PAAL PAYASAM .txt | 3902 | `d900a5ffc893` |
| SPINACH OMELETTE | SPINACH OMELETTE.zip | SPINACH OMELETTE.txt | 2859 | `9c834c937b6a` |
| VEG BURGER PATTY | VEG BURGER PATTY.zip |  VEG BURGER PATTY.txt | 1885 | `3d89a6550fb0` |
| CHI LEMON COR SP | CHI LEMON COR SP .zip | CHI LEMON COR SP .txt | 4991 | `b6274604d648` |
| ONION PAKODA | ONION PAKODA .zip | ONION PAKODA .txt | 1225 | `1e1c572215b1` |
| DAL MAKHANI | DAL MAKHANI.zip | DAL MAKHANI .txt | 3489 | `9f2973ea4fd6` |
| KUNG PAO CHICKEN | KUNG PAO CHICKEN.zip | KUNG PAO CHICKEN .txt | 2949 | `95b8050d9bcc` |
| MASOOR DAL | MASOOR DAL .zip | MASOOR DAL .txt | 2866 | `d43d56d75742` |
