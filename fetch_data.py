#!/usr/bin/env python3
"""Fetch all Hisn al-Muslim chapters and generate Kotlin data provider."""
import urllib.request
import json
import sys
import os

BASE = "http://www.hisnmuslim.com/api/ar"

def fetch_json(path):
    url = f"{BASE}/{path}"
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            return json.loads(r.read().decode("utf-8"))
    except Exception as e:
        print(f"Error fetching {url}: {e}", file=sys.stderr)
        return None

def main():
    chapters = fetch_json("husn_ar.json")
    if not chapters:
        print("Failed to fetch chapter list")
        return

    data = chapters.get("العربية", [])
    print(f"Found {len(data)} chapters")

    all_dhikrs = []

    for ch in data:
        cid = ch["ID"]
        title = ch["TITLE"]
        json_url = ch["TEXT"]
        # Extract filename
        filename = json_url.split("/")[-1]
        content = fetch_json(filename)
        if not content:
            print(f"  Skipping {cid}: {title}")
            continue

        arr = content.get("العربية", [])
        print(f"  Chapter {cid}: {title} ({len(arr)} entries)")

        chapter_dhikrs = []
        for item in arr:
            arabic = item.get("TEXT", "")
            repeat = item.get("COUNT", "")
            reference = item.get("REFERENCE", "")
            virtue = item.get("FADL", "")
            source = "السنة" if "القرآن" not in reference and "سورة" not in reference else "القرآن"
            hadith_grade = "صحيح"  # Hisn al-Muslim only contains authentic hadith

            chapter_dhikrs.append({
                "id": item.get("ID", 0),
                "category": title,
                "title": item.get("TITLE", ""),
                "arabic": arabic,
                "repeat": repeat,
                "virtue": virtue,
                "reference": reference,
                "source": source,
                "hadith_grade": hadith_grade,
                "notes": ""
            })

        all_dhikrs.append({
            "category_id": cid,
            "category": title,
            "items": chapter_dhikrs
        })

    # Write Python JSON output for inspection
    with open("/tmp/hisn_data.json", "w", encoding="utf-8") as f:
        json.dump(all_dhikrs, f, ensure_ascii=False, indent=2)

    # Generate Kotlin code
    generate_kotlin(all_dhikrs)

def generate_kotlin(all_data):
    lines = [
        "package com.urwah.dhikr",
        "",
        "object DhikrDataProvider {",
        "",
        "    private val allData: Map<String, List<DhikrItem>> = mapOf(",
    ]

    for idx, chapter in enumerate(all_data):
        cat = chapter["category"]
        items = chapter["items"]
        cat_var = f"cat{idx}"
        lines.append(f"        \"{cat}\" to {cat_var},")

    lines.append("    )")
    lines.append("")

    # Generate each category's data
    for idx, chapter in enumerate(all_data):
        cat = chapter["category"]
        items = chapter["items"]
        cat_var = f"cat{idx}"
        lines.append(f"    private val {cat_var} = listOf(")
        for item in items:
            # Escape the Arabic text properly
            arabic = item["arabic"].replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "")
            ref = item["reference"].replace("\\", "\\\\").replace('"', '\\"')
            virtue = item["virtue"].replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "")
            title = item["title"].replace("\\", "\\\\").replace('"', '\\"')
            notes = item["notes"].replace("\\", "\\\\").replace('"', '\\"')
            repeat = item["repeat"] if item["repeat"] else "1"

            lines.append(f"        DhikrItem(")
            lines.append(f'            id = {item["id"]},')
            lines.append(f'            category = "{cat}",')
            lines.append(f'            title = "{title}",')
            lines.append(f'            arabic = "{arabic}",')
            lines.append(f'            repeats = {repeat},')
            lines.append(f'            virtue = "{virtue}",')
            lines.append(f'            reference = "{ref}",')
            lines.append(f'            source = "{item["source"]}",')
            lines.append(f'            hadithGrade = "{item["hadith_grade"]}",')
            lines.append(f'            notes = "{notes}"')
            lines.append(f"        ),")
        lines.append("    )")
        lines.append("")

    lines.append("""    fun getDhikrs(categoryName: String): List<DhikrItem> {
        return allData[categoryName] ?: emptyList()
    }

    fun getAllCategories(): List<String> {
        return allData.keys.toList()
    }

    fun getCategoryCount(): Int = allData.size

    fun getTotalDhikrCount(): Int {
        return allData.values.sumOf { it.size }
    }
""")
    lines.append("}")

    output = "\n".join(lines)

    out_path = os.path.expanduser(
        "/home/obada/Projects/Android_Apps/orwa/app/src/main/java/com/urwah/dhikr/DhikrDataProvider.kt"
    )
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(output)

    print(f"\nGenerated {out_path}")
    print(f"Total categories: {len(all_data)}")
    for ch in all_data:
        print(f"  {ch['category']}: {len(ch['items'])} dhikrs")
    total = sum(len(ch['items']) for ch in all_data)
    print(f"Total dhikrs: {total}")

if __name__ == "__main__":
    main()
