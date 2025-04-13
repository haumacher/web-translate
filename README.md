# web-translate - Automatic translation of web-applications based on Thymeleaf templates

When using the [Thymeleaf template engine](https://www.thymeleaf.org) for rendering pages of a web-application, 
the official recommendation for internationalizing pages is to convert text on pages to resource keys referencing 
text snippes in resource bundles. 

Here is an example: `/WEB-INF/templates/chart.html`:
```
<h1>Your shopping cart</h1>
<p>The following items are ready for checkout:</p>
```

When internationalizing, this template becomes: `/WEB-INF/templates/chart.html`:
```
<h1 th:text="#{chart.title}"></h1>
<p th:text="#{chart.heading}"></p>
```
With properties `/WEB-INF/templates/chart_en.properties`:
```
chart.title=Your shopping cart
chart.heading=The following items are ready for checkout:
```
## Problems with the standard approch
This approach is perfectly valid for pages with few text elements that contain no intrinsic structure and 
formatting. However, when the application is more like a web site with many text elements, that contain links, 
buttons, formatting and so on, writing templates get cumbersome.

Even in the trivial case of a simple text with an embedded link, things get complicated:
```
<p>To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```
To internationalize this with simple resource keys, you have to split the sentence into three different keys and translate them separately:
```
instruction.1=To install PhoneBlock, you need a
instruction.2=FRITZ!Box Internet router from AVM
instruction.3=and a PhoneBlock account.
```
This approach produces hard to maintain templates and resource properties. Translating those properties is difficult. And automatic 
translation of those property files result in unaccepatble results, since the texts are not complete sentences with no useful 
context.

## Automatic translation with `web-translate`

With `web-translate` you write your application templates without caring about internationalization and let `web-translate` do the rest.
Templates are written in the native language of the application, which makes writing, reading and maintaining templates much more easy. 
The `web-translate` tools then extract resource properties from your templates, automatically translate properties into other languages
and synthesize additional templates for all languages you want to support.

While processing templates, `web-translate` assigns "translate IDs" to all HTML elements of your templates that contain text content 
that requires translation. But the text is not replaced with resource identifiers in your templates, which keeps them readable and 
understandable. Instead, text contents from your templates is automatically copied into property files for translation. However, those 
property files are only transient resources. After the properties are translated, `web-translate` synthesizes new versions of your 
templates in all supported languages of your application. Those generated templates need never be touched manually. If you change our
original template (in your native application language), you can repeat the transformation process to update all generated templates.

## Example process with `web-translate`
Let's have a look at the process considering the example from above. 

Assume the original template `/WEB-INF/templates/en-US/home.html`:
```
<p>To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```
When invoking `web-translate` for the source language `en-US` and the target languages `de` and `es`, this produces the following:

First of all, translate identifiers (`data-tx`) are assigned to text elements of your source template: 
```
<p data-tx="t0001">To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```
Then, a properties file is extracted from this template: `/WEB-INF/properties/en-US/home.properties`:
```
t0001=To install PhoneBlock, you need a <x1>FRITZ!Box Internet router from AVM</x1> and a PhoneBlock account.
```
In this resource file, the whole text is kept in a single property preserving the sentence structure. However, the technical 
content such as the link element and the `href` attribute is converted to an identifier tag `<x1>`. Such tag easily survives
automatic translation while minimizing the input to and potential erros during the translation process.

The properties are now translated to German and Spanish, e.g. `/WEB-INF/properties/de/home.properties`:
```
t0001=Um PhoneBlock zu installieren, benötigst Du einen <x1>FRITZ!Box Internet-Router von AVM</x1> und einen PhoneBlock-Account.
```
Afterwards, locale specific template variants are synthesized from your original template and the translation result. These
generated properties are to be served by your application, if a resource is requested in a specific language.

For German, the follwing template is produced: `/WEB-INF/templates/de/home.html`
```
<p>Um PhoneBlock zu installieren, benötigst Du einen <a th:href="@{/link/fritzbox}">FRITZ!Box Internet-Router von AVM</a> und einen PhoneBlock-Account.
```
During this synthetization process, the identifier tags `<x1>` are replaced by the technical variants from the original template. 
