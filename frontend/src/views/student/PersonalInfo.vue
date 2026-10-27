<template>
  <div class="my-info-page">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>👤 个人信息</span>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" style="max-width: 500px;">
        <el-form-item label="姓名">
          <el-input :value="store.nickname" disabled />
        </el-form-item>

        <el-form-item label="学号">
          <el-input :value="store.studentId" disabled />
        </el-form-item>

        <el-form-item label="班级" prop="className">
          <el-input v-model="form.className" placeholder="请输入班级，如：计算机2301" />
        </el-form-item>

        <el-divider />

        <el-form-item label="修改密码">
          <el-input v-model="form.oldPassword" type="password" placeholder="原密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="新密码（至少6位）" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入新密码" show-password />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="saveInfo" :loading="saving">保存修改</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import api from '../../api'

const store = useUserStore()
const demoStore = useDemoStore()
const formRef = ref(null)
const saving = ref(false)

const form = reactive({
  className: store.className || '',
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = {
  newPassword: [{ min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{
    validator: (rule, value, callback) => {
      if (value && value !== form.newPassword) callback(new Error('两次密码不一致'))
      else callback()
    }, trigger: 'blur'
  }],
}

async function saveInfo() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  if (demoStore.enabled) {
    if (form.className) { localStorage.setItem('cryd_class', form.className); store.className = form.className }
    ElMessage.success('保存成功（演示模式）')
    form.oldPassword = ''; form.newPassword = ''; form.confirmPassword = ''
    saving.value = false
    return
  }
  try {
    const payload = {
      id: store.id,
      className: form.className,
    }
    if (form.oldPassword && form.newPassword) {
      payload.oldPassword = form.oldPassword
      payload.newPassword = form.newPassword
    }
    const res = await api.put('/student/info', payload)
    if (res.data) {
      if (form.className) { localStorage.setItem('cryd_class', form.className); store.className = form.className }
      ElMessage.success('保存成功')
      form.oldPassword = ''
      form.newPassword = ''
      form.confirmPassword = ''
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.className = store.className || ''
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}

onMounted(() => { resetForm() })
</script>

<style scoped>
.my-info-page { padding: 0; }
.card-header { font-weight: 600; }
</style>