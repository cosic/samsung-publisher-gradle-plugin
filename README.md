# Samsung publisher gradle plugin

Samsung publisher gradle plugin is Android's unofficial release automation Gradle Plugin.
It helps you automate uploading of apk to samsung store. **The plugin haven't sent for review yet, it just downloads the apk**


## Quick start guide

### Obtainment credentials
Follow to [Samsung store](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/create-an-access-token.html#Create-a-service-account) and create service account with "Publishing & ITEM" permission.
Save `Private Key` and `Service Account ID`

Next, open the [application list](https://seller.samsungapps.com/main/sellerMain.as) and click on application.
You will see a link like that `https://seller.samsungapps.com/application/main.as?contentId=000000000000`.
Save `contentId` from link

### Installation
<details open><summary>Kotlin</summary>

```kt
//app build.gradle.kts
plugins {
    id("ru.litres.plugin.publish.samsung") version "{last_version}"
}
```

</details>

<details><summary>Groovy</summary>

```groovy
//app build.gradle.kts
plugins {
    id 'ru.litres.plugin.publish.samsung' version '{latest_version}'
}
```

</details>

<details><summary>Legacy</summary>
    <details open><summary>Kotlin</summary>

```kt
   //root build.gradle.kts
  dependencies {
    classpath("ru.litres.plugin:plugin:{latest_version}")
  }

  //app build.gradle.kts
  apply(plugin = "ru.litres.plugin.publish.samsung")
```

</details>

<details><summary>Groovy</summary>

```groovy
   //root build.gradle.kts
  dependencies {
    classpath "ru.litres.plugin:plugin:{latest_version}"
  }

  //app build.gradle.kts
  apply plugin: "ru.litres.plugin.publish.samsung"
```

</details>
</details>

### Common configuration
<details open><summary>Kotlin</summary>

```kt
android { ... }

samsungPublishConfig {
    //private key from service account
    privateKey.set(
        "-----BEGIN RSA PRIVATE KEY-----\n" +
            "....\n" +
            "......\n" +
            "-----END RSA PRIVATE KEY-----"
    )

    //Service Account ID from service account
    serviceAccountId.set("....")

    //Directory where plugin should find release apk.
    //Plugin searches by extension .apk and gets first file
    artifactDir.set(File("build/output"))

    //Object with app setting
    publishSetting {
        //contentId from url
        contentId = "..."
    }
}
```
</details>

<details><summary>Groovy</summary>

```groovy
android { ... }

samsungPublishConfig {
    //private key from service account
    privateKey.set(
        "-----BEGIN RSA PRIVATE KEY-----\n" +
            "....\n" +
            "......\n" +
            "-----END RSA PRIVATE KEY-----"
    )

    //Service Account ID from service account
    serviceAccountId.set("....")

    //Directory where plugin should find release apk.
    //Plugin searches by extension .apk and gets first file
    artifactDir.set(file("./build/output"))

    //Object with app setting
    publishSetting {
        //contentId from url
        contentId = "..."
    }
}
```
</details>

### Usage
And finally if your configuration is correct you can use gradle task `samsungPublish` for upload apk


## PublishSetting fields


| Field |  Type   |                                                                                                                              Description                                                                                                                              | Default value |
| :---   |:-------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|--------------:|
| contentId | String  |                                                                                                                 Application id which you get from url                                                                                                                 |             - |
| defaultLanguageCode  | String  | The language in which you provide application information. See [Language codes](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api-reference.html#publish-content-api-added-language-codes) for a list of supported languages. |           "RUS" |
| paid  | Boolean |                                                                                                             Whether app download requires a user payment                                                                                                              |         false |
| hasGoogleService  | Boolean |                                                                                                      Whether the app provides the user with any Google™ services                                                                                                      |          true |
| submitReview  | Boolean |                                                                                                      Whether app is submitted for review after uploading                                                                                                      |          false |
