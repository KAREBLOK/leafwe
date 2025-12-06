# 🍃 LeafWE

**LeafWE**, Minecraft sunucuları için geliştirilmiş, oyuncu dostu ve performans odaklı bir **hafifletilmiş WorldEdit alternatifidir**. Sunucu sahiplerinin oyuncularına güvenli bir şekilde yapı düzenleme yetkisi vermesini sağlar.

![Version](https://img.shields.io/badge/version-5.1.2-blue) ![Java](https://img.shields.io/badge/Java-17%2B-orange) ![Author](https://img.shields.io/badge/author-BaranMRJ-red)

---
dc
## 🌟 Özellikler

*   **🚀 Hafif ve Hızlı:** Sunucunuzu yormadan büyük alanlarda işlem yapabilme.
*   **🛡️ Geniş Bölge Koruması:** Aşağıdaki eklentilerle tam entegrasyon sağlar. Oyuncular sadece kendi yetkili oldukları alanlarda işlem yapabilir:
    *   **WorldGuard**
    *   **SuperiorSkyblock2**
    *   **Towny**
    *   **Lands** 🆕
    *   **GriefPrevention** 🆕
    *   **PlotSquared** 🆕
*   **⚡ Temel İşlemler:**
    *   **Set:** Seçili alanı belirli bir blokla doldurma.
    *   **Wall:** Seçili alanın etrafına duvar örme.
    *   **Replace:** Belirli blokları başkalarıyla değiştirme.
*   **↩️ Güvenli Geri Alma (Undo):** Hatalı işlemleri `undo` komutu ile geri alabilme.
*   **⚠️ Limit ve Onay Sistemi:**
    *   Belirlenen blok sayısının üzerindeki işlemler için oyunculardan onay ister.
    *   Her işlem için blok limiti belirleyerek sunucu performansını korur.
*   **🎒 Envanter Kontrolü:** (Opsiyonel) İşlem yapılırken gerekli blokların oyuncu envanterinden alınmasını sağlar.

---

## 📋 Gereksinimler

Bu eklentiyi çalıştırmak için sunucunuzda şunların bulunması önerilir:

*   **Java:** 17 veya daha yenisi
*   **Sunucu Yazılımı:** Paper, Spigot veya Purpur (1.19+)
*   **Temel Gereksinim:**
    *   [WorldEdit](https://dev.bukkit.org/projects/worldedit) (Seçim işlemleri için gereklidir)
*   **Desteklenen Koruma Eklentileri (Opsiyonel):**
    *   [WorldGuard](https://dev.bukkit.org/projects/worldguard)
    *   [SuperiorSkyblock2](https://www.spigotmc.org/resources/superiorskyblock2.87411/)
    *   Towny
    *   [Lands](https://www.spigotmc.org/resources/lands.53313/)
    *   [GriefPrevention](https://dev.bukkit.org/projects/griefprevention)
    *   [PlotSquared](https://www.spigotmc.org/resources/plotsquared-v6.77506/)

---

## 💻 Komutlar

| Komut | Açıklama | Kullanım |
| :--- | :--- | :--- |
| `/set <blok>` | Seçili alanı belirtilen blokla doldurur. | `/set stone` |
| `/wall <blok>` | Seçili alanın etrafına duvar örer. | `/wall glass` |
| `/replace <eski> <yeni>` | Seçili alandaki `eski` bloğu `yeni` blokla değiştirir. | `/replace dirt grass_block` |
| `/lwe undo` | Son yapılan işlemi geri alır. | `/lwe undo` |
| `/lwe confirm` | Bekleyen büyük işlemi onaylar. | `/lwe confirm` |
| `/lwe reload` | Yapılandırma dosyasını (config) yeniler. | `/lwe reload` |
| `/lwe give <oyuncu>` | Oyuncuya "WorldEdit Baltası" verir. | `/lwe give BaranMRJ` |
| `/lwe limits` | Kalan işlem limitinizi gösterir. | `/lwe limits` |

---

## 🔐 Yetkiler (Permissions)

| Yetki | Açıklama | Varsayılan |
| :--- | :--- | :--- |
| `leafwe.use` | `/set` komutunu kullanma yetkisi. | Herkes |
| `leafwe.wall` | `/wall` komutunu kullanma yetkisi. | Herkes |
| `leafwe.replace` | `/replace` komutunu kullanma yetkisi. | Herkes |
| `leafwe.undo` | İşlemleri geri alma yetkisi. | Herkes |
| `leafwe.give` | `/lwe give` komutunu kullanma yetkisi. | OP |
| `leafwe.reload` | Eklentiyi yenileme yetkisi. | OP |
| `leafwe.bypass.limit` | Blok sayısı limitine takılmama yetkisi. | OP |
| `leafwe.bypass.protection` | WG/Skyblock/Towny/Lands/GP korumalarını yok sayma. | OP |
| `leafwe.*` | Eklentideki tüm yetkilere sahip olma. | OP |

---

## ⚙️ Kurulum

1.  **LeafWE** `.jar` dosyasını indirin.
2.  Dosyayı sunucunuzun `plugins` klasörüne atın.
3.  Sunucuyu yeniden başlatın.
4.  `config.yml` dosyasından limitleri ve mesajları dilediğiniz gibi düzenleyin.
5.  `/lwe reload` komutu ile değişiklikleri uygulayın.

---

<div align="center">

**Geliştirici:** [KAREBLOK](https://github.com/KAREBLOK)  
**Web Sitesi:** [kareblok.tc](https://kareblok.tc)

</div>
