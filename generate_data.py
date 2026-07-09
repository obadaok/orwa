#!/usr/bin/env python3
"""Generate Kotlin DhikrDataProvider from downloaded Hisn al-Muslim API JSON files."""
import json
import os
import re

DATA_DIR = "/tmp/hisn_data"
OUTPUT = "/home/obada/Projects/Android_Apps/orwa/app/src/main/java/com/urwah/dhikr/DhikrDataProvider.kt"

def load_json(path):
    with open(path, encoding="utf-8-sig") as f:
        return json.load(f)

def escape_kotlin(s):
    if not s:
        return ""
    s = s.replace("\\", "\\\\")
    s = s.replace('"', '\\"')
    s = s.replace("\n", "\\n")
    s = s.replace("\r", "")
    s = re.sub(r'\s+', ' ', s)
    return s.strip()

def parse_repeat(val):
    if not val or str(val).strip() == "":
        return 1
    try:
        return int(val)
    except (ValueError, TypeError):
        s = str(val)
        nums = re.findall(r'\d+', s)
        if nums:
            return int(nums[0])
        return 1

def determine_source(title, text):
    quran_keywords = ["سورة", "القرآن", "آية", "البقرة", "الإخلاص", "الفلق", "الناس"]
    for kw in quran_keywords:
        if kw in title or kw in text[:100]:
            return "القرآن"
    return "السنة"

def get_chapter_list():
    """Get the chapter list from chapters.json."""
    data = load_json(os.path.join(DATA_DIR, "chapters.json"))
    return data["العربية"]

def main():
    chapters = get_chapter_list()
    total_dhikrs = 0
    category_data = []

    for ch in chapters:
        cid = ch["ID"]
        title = ch["TITLE"]
        json_path = os.path.join(DATA_DIR, f"ch_{cid}.json")

        if not os.path.exists(json_path):
            print(f"MISSING: {cid} {title}")
            continue

        try:
            data = load_json(json_path)
        except Exception as e:
            print(f"ERROR reading {cid} {title}: {e}")
            continue

        # Find the items array - key varies
        items_arr = None
        for key in data:
            if isinstance(data[key], list):
                items_arr = data[key]
                break

        if not items_arr:
            print(f"EMPTY: {cid} {title}")
            continue

        items = []
        for item in items_arr:
            item_id = item.get("ID", 0)
            item_text = item.get("ARABIC_TEXT", "") or item.get("TEXT", "") or ""
            item_repeat_raw = item.get("REPEAT", "") or item.get("COUNT", "") or "1"
            repeat = parse_repeat(item_repeat_raw)
            source = determine_source(title, item_text)

            items.append({
                "id": item_id,
                "category": title,
                "title": "",
                "arabic": item_text,
                "repeats": repeat,
                "virtue": "",
                "reference": "",
                "source": source,
                "hadithGrade": "صحيح",
                "notes": ""
            })
            total_dhikrs += 1

        category_data.append((cid, title, items))
        print(f"  [{cid:3d}] {title}: {len(items)} dhikrs")

    # Generate Kotlin file
    with open(OUTPUT, "w", encoding="utf-8") as f:
        f.write("package com.urwah.dhikr\n\n")
        f.write("object DhikrDataProvider {\n\n")

        # Map
        f.write("    private val dataMap: Map<String, List<DhikrItem>> = mapOf(\n")
        for cid, title, items in category_data:
            f.write(f'        "{escape_kotlin(title)}" to cat{cid},\n')
        f.write("    )\n\n")

        # Each category list
        for cid, title, items in category_data:
            f.write(f"    private val cat{cid} = listOf(\n")
            for it in items:
                f.write(f"        DhikrItem(\n")
                f.write(f'            id = {it["id"]},\n')
                f.write(f'            category = "{escape_kotlin(it["category"])}",\n')
                f.write(f'            title = "{escape_kotlin(it["title"])}",\n')
                arabic = it["arabic"]
                if len(arabic) > 1900:
                    arabic = arabic[:1900] + "..."
                f.write(f'            arabic = "{escape_kotlin(arabic)}",\n')
                f.write(f'            repeats = {it["repeats"]},\n')
                f.write(f'            virtue = "{escape_kotlin(it["virtue"])}",\n')
                f.write(f'            reference = "{escape_kotlin(it["reference"])}",\n')
                f.write(f'            source = "{escape_kotlin(it["source"])}",\n')
                f.write(f'            hadithGrade = "{escape_kotlin(it["hadithGrade"])}",\n')
                f.write(f'            notes = "{escape_kotlin(it["notes"])}"\n')
                f.write(f"        ),\n")
            f.write(f"    )\n\n")

        # Public methods
        f.write("""    fun getDhikrs(categoryName: String): List<DhikrItem> {
        return dataMap[categoryName] ?: emptyList()
    }

    fun getAllCategories(): List<String> {
        return dataMap.keys.toList()
    }

    fun getCategoryCount(): Int = dataMap.size

    fun getTotalDhikrCount(): Int {
        return dataMap.values.sumOf { it.size }
    }
}\n""")

    print(f"\n{'='*50}")
    print(f"Generated: {OUTPUT}")
    print(f"Total categories: {len(category_data)}")
    for cid, title, items in category_data:
        print(f"  [{cid:3d}] {title}: {len(items)}")
    print(f"Total dhikrs: {total_dhikrs}")
    print(f"{'='*50}")

if __name__ == "__main__":
    main()
