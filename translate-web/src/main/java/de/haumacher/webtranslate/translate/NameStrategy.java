package de.haumacher.webtranslate.translate;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.haumacher.webtranslate.extract.PropertiesExtractor;

public enum NameStrategy {
	LANG_TAG_DIR() {
		@Override
		protected File destDir(File propertiesDir, String destLang) {
			return new File(propertiesDir, destLang);
		}
		
		@Override
		protected Path destPath(Path path, String destLang) {
			return path;
		}
		
	}, 
	
	LANG_TAG_NAME() {
		@Override
		protected File destDir(File propertiesDir, String destLang) {
			return propertiesDir;
		}
		
		@Override
		protected Path destPath(Path path, String destLang) {
			String fileName = path.getFileName().toString();
			String destName = PropertiesExtractor.baseName(fileName) + "_" + destLang.replace('-', '_') + ".properties";
			if (path.getParent() == null) {
				return Paths.get(destName);
			} else {
				return path.getParent().resolve(destName);
			}
		}
	};

	protected abstract File destDir(File propertiesDir, String destLang);

	protected abstract Path destPath(Path path, String destLang);
}