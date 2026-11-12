package com.acme.salary.bulkimport;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** CSV import endpoints (api-contracts.md section 5a). Any signed-in user can import. */
@RestController
@RequestMapping("/api/employees/import")
class ImportController {

    private final ImportService importService;

    ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/template")
    ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("employees-import-template.csv").build().toString())
                .body(importService.template());
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportReport preview(@RequestParam("file") MultipartFile file) {
        return importService.preview(file);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ImportService.ImportResult importFile(@RequestParam("file") MultipartFile file) {
        return importService.importFile(file);
    }
}
