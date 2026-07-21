<template>
    <div id="userManagePage">
        <h2>用户管理</h2>

        <!-- 搜索表单 -->
        <a-form layout="inline" :model="searchParams" @finish="doSearch">
            <a-form-item label="账号">
                <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
            </a-form-item>
            <a-form-item label="昵称">
                <a-input v-model:value="searchParams.userName" placeholder="输入昵称" allow-clear />
            </a-form-item>
            <a-form-item>
                <a-button type="primary" html-type="submit">搜索</a-button>
                <a-button style="margin-left: 10px" @click="resetSearch">重置</a-button>
            </a-form-item>
        </a-form>

        <a-divider />

        <a-button type="primary" @click="showAddModal" style="margin-bottom: 16px">
            添加用户
        </a-button>

        <a-table :columns="columns" :data-source="data" :pagination="pagination" :loading="loading"
            @change="doTableChange" row-key="id" :scroll="{ x: 1300 }">
            <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'userAvatar'">
                    <a-avatar :src="record.userAvatar" />
                </template>
                <template v-else-if="column.dataIndex === 'userRole'">
                    <a-tag v-if="record.userRole === 'admin'" color="gold">管理员</a-tag>
                    <a-tag v-else color="blue">普通用户</a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'userStatus'">
                    <a-tag v-if="record.userStatus === 'disabled'" color="error">已禁用</a-tag>
                    <a-tag v-else color="success">正常</a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'createTime'">
                    {{ record.createTime ? dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
                </template>
                <template v-else-if="column.key === 'action'">
                    <a-space>
                        <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
                        <a-button type="link" size="small" danger @click="doDelete(record.id)">删除</a-button>
                    </a-space>
                </template>
            </template>
        </a-table>

        <!-- 添加/编辑用户弹窗 -->
        <a-modal v-model:open="modalVisible" :title="isEdit ? '编辑用户' : '添加用户'" @ok="handleSubmit"
            @cancel="modalVisible = false" :confirm-loading="submitting" width="520px">
            <a-form :model="formData" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
                <a-form-item label="账号" required>
                    <a-input v-model:value="formData.userAccount" placeholder="请输入账号" :disabled="isEdit" />
                </a-form-item>
                <a-form-item label="昵称">
                    <a-input v-model:value="formData.userName" placeholder="请输入昵称" />
                </a-form-item>
                <a-form-item label="头像">
                    <a-input v-model:value="formData.userAvatar" placeholder="头像地址" />
                </a-form-item>
                <a-form-item label="简介">
                    <a-textarea v-model:value="formData.userProfile" placeholder="用户简介" :rows="3" />
                </a-form-item>
                <a-form-item label="角色" v-if="isEdit">
                    <a-select v-model:value="formData.userRole" placeholder="选择角色">
                        <a-select-option value="user">普通用户</a-select-option>
                        <a-select-option value="admin">管理员</a-select-option>
                    </a-select>
                </a-form-item>
                <a-alert v-if="!isEdit" type="info" show-icon message="新增用户的默认密码为 123456，请提醒用户及时修改" />
            </a-form>
        </a-modal>
    </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { listUserVoByPage, addUser, updateUser, deleteUser } from '@/api/userController'
import dayjs from 'dayjs'

// 表格列定义
const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '头像', dataIndex: 'userAvatar', width: 70 },
    { title: '账号', dataIndex: 'userAccount', width: 140 },
    { title: '昵称', dataIndex: 'userName', width: 120 },
    { title: '角色', dataIndex: 'userRole', width: 100 },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    { title: '操作', key: 'action', width: 280, fixed: 'right' },
]

const data = ref<API.LoginUserVO[]>([])
const loading = ref(false)

// 搜索参数
const searchParams = reactive<API.UserQueryRequest>({
    current: 1,
    pageSize: 10,
    userAccount: '',
    userName: '',
})

// 分页配置
const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
})

// 加载用户列表
const fetchData = async () => {
    loading.value = true
    try {
        const res = await listUserVoByPage(searchParams)
        if (res.data.code === 200 && res.data.data) {
            data.value = res.data.data.records || []
            pagination.value.total = res.data.data.totalRow || 0
            pagination.value.current = searchParams.current || 1
            pagination.value.pageSize = searchParams.pageSize || 10
        } else {
            message.error(res.data.msg || '加载失败')
        }
    } catch (error) {
        message.error('加载失败')
    } finally {
        loading.value = false
    }
}

// 搜索
const doSearch = () => {
    searchParams.current = 1
    fetchData()
}

// 重置搜索
const resetSearch = () => {
    searchParams.userAccount = ''
    searchParams.userName = ''
    searchParams.current = 1
    fetchData()
}

// 表格分页变化
const doTableChange = (pag: { current: number; pageSize: number }) => {
    searchParams.current = pag.current
    searchParams.pageSize = pag.pageSize
    fetchData()
}

// ==================== 添加/编辑 ====================
const modalVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)

const createDefaultFormData = (): API.UserAddRequest & API.UserUpdateRequest => ({
    id: undefined,
    userAccount: '',
    userName: '',
    userAvatar: '',
    userProfile: '',
    userRole: 'user',
})

const formData = reactive(createDefaultFormData())

const showAddModal = () => {
    isEdit.value = false
    Object.assign(formData, createDefaultFormData())
    modalVisible.value = true
}

const showEditModal = (record: API.LoginUserVO) => {
    isEdit.value = true
    Object.assign(formData, {
        id: record.id,
        userAccount: record.userAccount,
        userName: record.userName,
        userAvatar: record.userAvatar,
        userProfile: record.userProfile,
        userRole: record.userRole || 'user',
    })
    modalVisible.value = true
}

const handleSubmit = async () => {
    if (!isEdit.value && !formData.userAccount) {
        message.warning('请填写账号')
        return
    }
    submitting.value = true
    try {
        const res = isEdit.value ? await updateUser(formData) : await addUser(formData)
        if (res.data.code === 200) {
            message.success(isEdit.value ? '更新成功' : '添加成功')
            modalVisible.value = false
            fetchData()
        } else {
            message.error(res.data.msg || '操作失败')
        }
    } catch (error) {
        message.error('操作失败')
    } finally {
        submitting.value = false
    }
}

// ==================== 删除 ====================
const doDelete = (userId?: number) => {
    if (!userId) {
        return
    }
    Modal.confirm({
        title: '确认删除',
        content: '确定要删除该用户吗？此操作不可恢复。',
        okType: 'danger',
        onOk: async () => {
            const res = await deleteUser({ id: userId })
            if (res.data.code === 200) {
                message.success('删除成功')
                fetchData()
            } else {
                message.error('删除失败：' + res.data.msg)
            }
        },
    })
}

onMounted(() => {
    fetchData()
})
</script>

<style scoped>
#userManagePage {
    padding: 16px;
}
</style>
