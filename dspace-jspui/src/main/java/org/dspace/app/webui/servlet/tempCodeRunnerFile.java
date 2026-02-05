// try {
//     NodeList linkNodes = record.getElementsByTagName("Link");
//     if (linkNodes.getLength() > 0) {
//         Element linkElement = (Element) linkNodes.item(0);
//         String linkValue = null;

//         // Получаем значение из элемента Value
//         Node valueNode = linkElement.getElementsByTagName("Value").item(0);
//         if (valueNode != null) {
//             linkValue = valueNode.getTextContent().trim();
//         }

//         // Если в Value ничего нет — берём текст самого <Link>
//         if (linkValue == null || linkValue.isEmpty()) {
//             linkValue = linkElement.getTextContent().trim();
//         }

//         if (linkValue != null && !linkValue.isEmpty()) {

//             // ----- Преобразуем UNC в HTTP -----
//             // \\10.100.100.23\library\Metod_ukaz\file.pdf
//             String unc = linkValue.replaceFirst("^\\\\\\\\", "");

//             String[] parts = unc.split("\\\\", 3);
//             if (parts.length < 3) {
//                 throw new RuntimeException("Invalid UNC path: " + linkValue);
//             }

//             String host = parts[0];   // 10.100.100.23
//             String share = parts[1]; // library
//             String path = parts[2];  // Metod_ukaz\Терентьев...

//             // \ → /
//             path = path.replace("\\", "/");

//             // Имя файла
//             String filenamelel = path.substring(path.lastIndexOf('/') + 1);

//             // Кодируем ТОЛЬКО путь
//             String encodedPath = URLEncoder.encode(path, "UTF-8")
//                     .replace("+", "%20")
//                     .replace("%2F", "/");

//             String fileUrl = "http://" + host + "/" + encodedPath;

//             // ----- Логи -----
//             log.info("Original UNC: " + linkValue);
//             log.info("HTTP decoded: http://" + host + "/" + path);
//             log.info("HTTP encoded: " + fileUrl);

//             itemItem.addMetadata("dc", "textpart", null, null, "");

//             // ----- Скачивание и сохранение в DSpace -----
//             try (InputStream iss = new URL(fileUrl).openStream()) {

//                 if (!exists) {
//                     itemItem.createBundle("ORIGINAL");
//                     Bitstream b = itemItem.getBundles("ORIGINAL")[0].createBitstream(iss);
//                     b.setName(filenamelel);
//                     b.setDescription("from 1C");
//                     b.setSource("1C");
//                     itemItem.getBundles("ORIGINAL")[0].setPrimaryBitstreamID(b.getID());
//                     BitstreamFormat bf = FormatIdentifier.guessFormat(context, b);
//                     b.setFormat(bf);
//                     b.update();
//                 }
//                 itemItem.update();
//             }
//         }
//     }
// } catch (Exception e) {
//     log.error("Error while attaching PDF", e);
// }