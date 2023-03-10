package nl.wearefrank.openapifrankadapter.schemas;

import nl.nn.adapterframework.xml.SaxElementBuilder;
import nl.wearefrank.openapifrankadapter.schemas.Types.Typing;
import org.xml.sax.SAXException;

import java.util.ArrayList;

public class Sequence {
    private final ArrayList<Element> elements;

    public Sequence() {
        this.elements = new ArrayList<>();
    }

    // Add element to elements
    public void addElement(Element element) {
        this.elements.add(element);
    }

    // Generic function to add type: complexType or simpleType
    public void addTyping(Typing typing) {
        this.elements.add(new Element(typing));
    }

    public void AddSequenceToBuilder(SaxElementBuilder builder) throws SAXException {
        try (SaxElementBuilder subElement = builder.startElement("xs:sequence")) {
            for (Element element : this.elements) {
                element.AddElementToBuilder(subElement);
            }
        }
    }

    public void SilentAddSequenceToBuilder(SaxElementBuilder builder) throws SAXException {
        for (Element element : this.elements) {
            element.AddElementToBuilder(builder);
        }
    }
}