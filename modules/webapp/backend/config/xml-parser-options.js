/**
 * It returns the basic options for the xml/js parser.
 * @param {string} attributeNamePrefix - default value is an empty string
 */
function getParserOptions(attributeNamePrefix = '') {
  return {
    attributeNamePrefix,
    ignoreAttributes: false,
    format: true,
    indentBy: '  ',
    supressEmptyNode: true
  }
}

const xmlParserOptions = {
  getParserOptions
};

module.exports = xmlParserOptions;