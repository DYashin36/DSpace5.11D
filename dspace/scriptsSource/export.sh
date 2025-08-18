#!/bin/bash
# Экспорт всех коллекций из файла collections.tsv

# === Настройки ===
EXPORT_DIR="/opt/transfer"      # куда сохранять SAF
DSpace_HOME="/opt/dspace"       # путь к DSpace
INPUT_FILE="collections.tsv"    # файл со списком коллекций

# === Проверка наличия входного файла ===
if [ ! -f "$INPUT_FILE" ]; then
  echo "Файл $INPUT_FILE не найден!"
  exit 1
fi

# === Создаём папку экспорта ===
mkdir -p "$EXPORT_DIR"

# === Цикл по строкам TSV ===
while IFS=$'\t' read -r id handle name
do
    # пропускаем пустые строки или строки без handle
    if [ -z "$handle" ]; then
        continue
    fi

    # делаем имя папки: заменяем пробелы на "_", убираем лишнее
    folder_name=$(echo "$name" | tr ' ' '_' | tr -cd '[:alnum:]_-')
    if [ -z "$folder_name" ]; then
        folder_name="collection_${id}"
    fi

    target_dir="${EXPORT_DIR}/${folder_name}"

    echo ">>> Экспорт: $name ($handle) → $target_dir"

    mkdir -p "$target_dir"

    # сам экспорт
    ${DSpace_HOME}/bin/dspace export \
        -t COLLECTION \
        -i "$handle" \
        -d "$target_dir" \
        -m -n 0

done < "$INPUT_FILE"