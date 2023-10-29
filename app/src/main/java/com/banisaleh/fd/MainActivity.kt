package com.banisaleh.fd

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.banisaleh.fd.databinding.ActivityMainBinding
import com.github.dhaval2404.imagepicker.ImagePicker
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.*

class MainActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener, ImageClassifierHelper.ClassifierListener {
    private lateinit var uri: Uri
    private lateinit var binding: ActivityMainBinding
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var classifierEyes: ImageClassifierHelper
    private lateinit var classifierColor: ImageClassifierHelper
    private lateinit var bitmapBuffer: Bitmap
    private var scaleFactor: Float = 1f

    private var takeGallery =   registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val resultCode = result.resultCode
        val data = result.data

        when (resultCode) {
            Activity.RESULT_OK -> {
                val fileUri = data?.data!!
                uri = fileUri
                binding.imageView.setImageURI(fileUri)
                detectObjects()
            }
            ImagePicker.RESULT_ERROR -> {
                Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Task Cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        objectDetectorHelper = ObjectDetectorHelper(
            context = this@MainActivity,
            objectDetectorListener = this
        )

        classifierEyes = ImageClassifierHelper(
            context = this@MainActivity,
            imageClassifierListener = this
        )

        classifierColor = ImageClassifierHelper(
            context = this@MainActivity,
            imageClassifierListener = this,
            currentModel = 1
        )


        binding.fbGallery.setOnClickListener {
            ImagePicker.with(this)
                .crop(1f, 1f)
                .createIntent { intent ->
                    takeGallery.launch(intent)
                }
        }
    }

    private fun detectObjects() {

        bitmapBuffer =  when {
            Build.VERSION.SDK_INT < 28 -> MediaStore.Images.Media.getBitmap(
                this.contentResolver,
                uri
            )
            else -> {
                val source = ImageDecoder.createSource(this.contentResolver, uri)
                ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
            }
        }

        objectDetectorHelper.detect(bitmapBuffer, 0)
    }

    override fun onError(error: String) {
        TODO("Not yet implemented")
    }

    override fun onResults(results: List<Classifications>?, inferenceTime: Long, currentModel: Int) {
        val cfValue = listOf("Segar", "Tidak Segar")
        for (result in results ?: LinkedList<Classifications>()){
            if (currentModel == ImageClassifierHelper.MODEL_EYES){
                binding.eyeTv.text = cfValue[result.categories[0].label.toInt()]
            } else {
                binding.colorTv.text = cfValue[result.categories[0].label.toInt()]
            }
        }
    }

    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {

        binding.eyeImage.setImageResource(R.mipmap.default_image);
        binding.colorImage.setImageResource(R.mipmap.default_image);
        binding.eyeTv.text = "Tidak Terdeteksi"
        binding.colorTv.text = "Tidak Terdeteksi"

        for (result in results ?: LinkedList<Detection>()) {
            val bitmap = bitmapBuffer
            val boundingBox = result.boundingBox
            var left = boundingBox.left
            var top = boundingBox.top
            var width = boundingBox.right - boundingBox.left
            var height = boundingBox.bottom - boundingBox.top

            if (left < 0f )
                left = 0f

            if (top < 0f )
                top = 0f

            if (left + width > bitmap.width)
                width = bitmap.width - left

            if (top + height > bitmap.height)
                height = bitmap.height.toFloat() - top

            val cropped = Bitmap.createBitmap(
                bitmap,
                left.toInt(),
                top.toInt(),
                width.toInt(),
                height.toInt()
            )

            if (result.categories[0].label.equals("fish")) {
                binding.colorImage.setImageBitmap(cropped)
                classifierColor.classify(cropped, 0)
            } else {
                binding.eyeImage.setImageBitmap(cropped)
                classifierEyes.classify(cropped, 0)
            }
        }
    }
}