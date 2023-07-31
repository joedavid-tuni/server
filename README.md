# WebServer

[![CC BY 4.0][cc-by-shield]][cc-by]

This git repository contains supplementary material to the Doctoral Dissertation of Joe David, "_A Design Science Research Approach to Architecting and Developing Information Systems for Collaborative Manufacturing: A Case for Human-Robot Collaboration_". 

> **Note**: For other (dissertation) related git repositories, see the meta git repository [here](https://permanent.link/to/jd-doctoral-dissertation/meta-repository).

## Pre-requisites

All dependencies are managed by MAVEN and listed in the `pom.xml` file. Briefly, the main libraries used to implement semantic web technologies are [Apache Jena](https://jena.apache.org/) (for RDF, OWL, Fuseki, SHACL, etc.), [Openllet](https://github.com/Galigator/openllet) OWL 2DL Reasoner, and [JADE](https://jade.tilab.com/) as the agent middleware.

## Getting Started

The entry point to the web server is the `SimpleServer.java` in [src/main/java](./src/main/java/SimpleServer.java). It spawns all the agents and sets up their private knowledge bases using a developed [ontology](https://joedavid-tuni.github.io/ontologies/camo/).

## Citation

Under the included [LICENSE](./LICENSE), if you use or extend the application, especially in an academic context, please cite. You can click "Cite this repository" on the right sidebar to copy both `APA` and `BibTeX` formatted citation.

## License

This work is licensed under a [Creative Commons Attribution 4.0 International License][cc-by]. You can find the included license [here](./LICENSE).

[![CC BY 4.0][cc-by-image]][cc-by]

[cc-by]: http://creativecommons.org/licenses/by/4.0/
[cc-by-image]: https://i.creativecommons.org/l/by/4.0/88x31.png
[cc-by-shield]: https://img.shields.io/badge/License-CC%20BY%204.0-lightgrey.svg