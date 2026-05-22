package com.jasmine.studioai.literature.internal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LiteratureRegistryFile {

    private List<StoredLiteratureItem> items = new ArrayList<>();
}
