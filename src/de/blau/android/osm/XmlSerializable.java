package de.blau.android.osm;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

public interface XmlSerializable {

	public void toXml(XmlSerializer serializer, long changeSetId)
			throws IllegalArgumentException, IllegalStateException, IOException;

}
